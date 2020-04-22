package com.github.opengrabeso.loctio
package rest

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.softwaremill.sttp.HttpURLConnectionBackend
import common.FileStore
import common.model._
import common.model.github._
import io.udash.rest.raw.HttpErrorException

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import shared.FutureAt._
import shared.ChainingSyntax._

object UserRestAPIServer {
  case class CommentContent(
    linkUrl: String,
    linkText: String,
    html: String,
    author: String,
    time: ZonedDateTime
  )

  case class TraySession(
    sessionStarted: ZonedDateTime, // used to decide when should be flush (reset) the session to perform a full update
    lastModified: Option[String], // lastModified HTTP headers used for notifications optimization
    lastPoll: ZonedDateTime, // last time when the user has polled notifications
    currentMesages: Seq[Notification],
    lastComments: Map[String, CommentContent], // we use URL for identification, as this is sure to be stable for an issue
    mostRecentNotified: Option[ZonedDateTime] // most recent notification display to the user
  )

}

import UserRestAPIServer._

class UserRestAPIServer(val userAuth: Main.GitHubAuthResult) extends UserRestAPI with RestAPIUtils {
  import UserRestAPI._

  private def checkState(state: String) = {
    state match {
      case "online" | "offline" | "busy" | "away" | "invisible" =>
      case _ =>
        throw HttpErrorException(400, s"Unknown state $state")
    }
  }

  private def checkIpAddress(addr: String) = {
    val Valid = "[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+".r
    addr match {
      case Valid() =>
      case _ =>
        throw HttpErrorException(400, s"Invalid IP address $name")
    }
  }


  def name = syncResponse {
    (userAuth.login, userAuth.fullName)
  }

  private def listUsersSync(ipAddress: String, state: String) = {
    checkState(state)
    checkIpAddress(ipAddress)
    // when the user is away or invisible, do not update his presence
    if (state != "away" && state != "invisible") {
      //println(s"Presence.reportUser ${userAuth.login} $state")
      if (state == "offline") {
        // when going offline, report only when we were not offline yet
        // beware: the same user may use several clients at the same time
        if (Presence.getUser(userAuth.login).forall(_.state != "offline")) {
          Presence.reportUser(userAuth.login, ipAddress, state)
        }
      } else {
        Presence.reportUser(userAuth.login, ipAddress, state)
      }
    }
    Presence.listUsers
  }
  def listUsers(ipAddress: String, state: String) = syncResponse {
    listUsersSync(ipAddress, state)
  }

  def trayUsersHTML(ipAddress: String, state: String) = syncResponse {

    val us = listUsersSync(ipAddress, state)

    val table = common.UserState.userTable(userAuth.login, false, us)

    def getUserStatusIcon(state: String) = {
      s"<img class='state icon' src='static/small/user-$state.png'></img>"
    }

    def displayTime(t: ZonedDateTime) = {
      s"<time>$t</time>"
    }

    def userRowHTML(row: common.model.UserRow) = {
      //language=HTML
      s"""<tr>
           <td>${getUserStatusIcon(row.currentState)}</td>
           <td class="username"><a href="https://www.github.com/${row.login}">${row.login}</a></td>
           <td>${row.location}</td>
           <td>${if (row.currentState != "online") displayTime(row.lastTime) else ""}</td>
           </tr>
          """
    }

    val columns = Seq("", "User", "Location", "Last seen")

    val tableHTML = //language=HTML
      s"""<html>
            <head>
            <link href="static/tray.css" rel="stylesheet" />
            </head>
            <body class="users">
              <table>
              <tr>${columns.map(c => s"<th>$c</th>").mkString}</tr>
              ${table.map(userRowHTML).mkString}
              </table>
            </body>
           </html>
        """

    def trayUserLine(u: UserRow) = {

      def userStateDisplay(state: String) = {
        state match { // from https://www.alt-codes.net/circle-symbols
          case "online" => "⚫"
          case "offline" => "⦾"
          case "away" => "⦿"
          case "busy" => "⚫"
        }
      }

      val stateText = userStateDisplay(u.currentState)
      if (u.currentState != "offline") {
        s"$stateText ${u.login}: ${u.location}"
      } else {
        s"$stateText ${u.login}: ${displayTime(u.lastTime)}"
      }
    }
    val onlineUsers = table.filter(u => u.login != userAuth.login && u.lastTime.until(ZonedDateTime.now(), ChronoUnit.DAYS) < 7)
    val statusText = onlineUsers.map(u => trayUserLine(u)).mkString("\n")

    (tableHTML, statusText)
  }

  def setLocationName(login: String, name: String) = syncResponse {
    // no need to sanitize login name, it is used only to query the file
    // check last ip address for the user
    Presence.getUser(login).map { presence =>
      Locations.nameLocation(presence.ipAddress, name)
      Presence.listUsers
    }.getOrElse(throw HttpErrorException(500, "User presence not found"))
  }

  private val sessionFilename = FileStore.FullName("tray", userAuth.login)

  def shutdown(data: RestString) = syncResponse {
    println(s"Received ${userAuth.login} shutdown ${data.value}")
    // value other then now can be used for testing and debugging the protocol
    if (data.value == "now") {
      Presence.getUser(userAuth.login).filter(_.state != "offline").foreach { p =>
        Presence.reportUser(userAuth.login, p.ipAddress, "offline")
      }
      // TODO: use some reliable client identification
      // we use shutdown for this, but shutdown may come from web as well
      // we could also apply some heuristics (reset user which was not polling client
      Storage.delete(sessionFilename)
    }
  }

  def trayNotificationsHTML() = syncResponse {
    val sttpBackend = HttpURLConnectionBackend()
    try {

      val gitHubAPI = new GitHubAPIClient(sttpBackend)

      val session = Storage.load[TraySession](sessionFilename)
      val now = ZonedDateTime.now()

      // heuristics to handle missing shutdown
      // user should be polling notification frequently (about once per minute).
      val recentSession = session.filter { s =>
        ChronoUnit.MINUTES.between(s.lastPoll, now) < 5 && // Long pause probably means the client was terminated and should get a fresh state
        ChronoUnit.MINUTES.between(s.sessionStarted, now) < 120 // each two hours perform a full reset
      }

      val ifModifiedSince = recentSession.flatMap(_.lastModified).orNull

      //println(s"notifications.get ${userAuth.login}: since $ifModifiedSince")

      val r = gitHubAPI.api.authorized("Bearer " + userAuth.token).notifications.get(
        ifModifiedSince,
        //all = true
      ).at(executeNow).transform {
        case Success(response) =>
          val (newUnread, read) = response.data.partition(_.unread)

          val oldUnread = recentSession.filter(_.lastModified.nonEmpty).map(_.currentMesages).getOrElse(Seq.empty)
          // add messages reported as unread, but only when they are not present yet, insert recent messages first
          val addUnread = newUnread.diff(oldUnread)

          // download last comments for the new content
          val newComments = addUnread.filter(_.subject.`type` == "Issue").map { n =>
            // the issue may have no comments
            import rest.github.EnhancedRestImplicits._

            // if there is a comment, the only reason why we need to get the issue is to get its number (we need it for the link)
            val issue = gitHubAPI.request[Issue](n.subject.url, userAuth.token)
            val prefix = s"https://www.github.com/${n.repository.full_name}/issues/${issue.number}"
            val comment = Option(n.subject.latest_comment_url).filter(_.nonEmpty).map(url => gitHubAPI.request[Comment](url, userAuth.token))
            // if there is a last comment, obtain it, when not, use the issue
            val (linkUrl, by, time, body) = comment.map { c =>
              println(s"Get comment ${c.id}")
              (prefix + "#issuecomment-" + c.id.toString, c.user.login, c.updated_at, c.body)
            }.getOrElse {
              // this does not seem to happen - it seems the issue is also accessible as a comment
              println(s"Get issue ${issue.number}")
              (prefix, issue.user.login, issue.updated_at, issue.body)
            }

            val markdown = gitHubAPI.api.authorized("Bearer " + userAuth.token).markdown.markdown(body).awaitNow
            n.subject.url -> CommentContent(
              linkUrl,
              s"#${issue.number}",
              markdown.data, by, time
            )
          }

          // remove any message reported as read (I do not think this ever happens, but if it would, we would handle it)
          val unread = addUnread ++ oldUnread.diff(read)
          val comments = recentSession.map(_.lastComments).getOrElse(Map.empty) ++ newComments

          println(s"${userAuth.login}: since $ifModifiedSince new: ${newUnread.size}, read: ${read.size}, unread: ${unread.size}")
          // response content seems inconsistent. Sometimes it contains messages
          def notificationHTML(n: common.model.github.Notification, comments: Map[String, CommentContent]) = {
            //language=HTML
            val comment = comments.get(n.subject.url)
            def ifComment(s: CommentContent => String) = comment.map(s).getOrElse("")
            val header = s"""
            <tr><td class="notification header">
              <span class="message link">
              ${ifComment(c => s"<a href='${c.linkUrl}'>${c.linkText}</a>")}
              </span>

              <span class="message title">${n.subject.title}</span><br/>
              ${n.repository.full_name}
              <span class="message time"><time>${n.updated_at}</time></span>
              <span class="message reason ${n.reason}">${n.reason}</span>
             </td></tr>
             """
            comments.get(n.subject.url).map { commentHtml =>
              header ++
                s"""
                   <tr><td class="notification signature">
                   <span class="message by">${commentHtml.author}</span>
                   <span class="message time"><time>${commentHtml.time}</time></span>
                   </td></tr>
                   <tr><td class="notification body">${commentHtml.html}</td></tr>
                   """
            }.getOrElse(header)
          }

          val notificationsTable =
          //language=HTML
            s"""<html>
              <head>
              <link href="static/tray.css" rel="stylesheet" />
              </head>
              <body class="notifications">
                <p><a href="https://www.github.com/notifications">GitHub notifications</a></p>
                <table>
                ${unread.map(notificationHTML(_, comments)).mkString}
                </table>
              </body>
             </html>
          """

          // is it really sorted by updated_at, or some other (internal) update timestamp (e.g. when last_read_at is higher than updated_at?)
          val mostRecentNofified = recentSession.flatMap(_.mostRecentNotified)
          val newMostRecentNotified = unread.headOption.map(_.updated_at)
          // avoid flooding the notification area in case the user has many notifications
          // reverse to display oldest first
          import common.Util._
          val notifyUserAbout = mostRecentNofified.map { notifyFrom =>
            // check newUnread only - old unread were already reported if necessary
            newUnread.filter(_.updated_at > notifyFrom).tap { u =>
              println(s"Take notified from $notifyFrom: ${u.size}, unread times: ${newUnread.map(_.updated_at)}")
            }
            // beware: two messages might have identical timestamps. Unlikely, but possible
            // such message may have a notification skipped
          }.getOrElse(newUnread).take(5).reverse

          val notifyUser = notifyUserAbout.map(_.subject.title)

          val newSession = TraySession(
            recentSession.map(_.sessionStarted).getOrElse(now),
            response.headers.lastModified,
            now,
            unread,
            comments,
            newMostRecentNotified
          )
          Storage.store(sessionFilename, newSession)

          val nextAfter = response.headers.xPollInterval.map(_.toInt).getOrElse(60)
          Success(notificationsTable, notifyUser, nextAfter)
        case Failure(rest.github.DataWithHeaders.HttpErrorExceptionWithHeaders(ex, headers)) =>
          val nextAfter = headers.xPollInterval.map(_.toInt).getOrElse(60)

          for (s <- recentSession) { // update the session info to keep alive (prevent resetting)
            Storage.store(sessionFilename, s.copy(lastPoll = now))
          }

          Success("", Seq.empty, nextAfter)
        case Failure(ex) =>
          Failure(ex)
      }
      // it would be nice to pass Future directly, but somehow it does not work - probably some Google App Engine limitation
      Await.result(r, Duration(60, SECONDS))

      //      trayNotificationsImpl(sttpBackend)
    } finally {
      sttpBackend.close()
    }
  }


}
