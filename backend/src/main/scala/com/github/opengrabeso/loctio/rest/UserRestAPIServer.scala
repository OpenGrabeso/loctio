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
import scala.util.{Failure, Success, Try}
import shared.FutureAt._
import shared.ChainingSyntax._
import common.Util._

object UserRestAPIServer {
  trait NotificationContent {
    def htmlResult: String
  }
  case class CommentContent(
    linkUrl: String,
    linkText: String,
    html: String,
    author: String,
    time: ZonedDateTime
  ) extends NotificationContent {
    override def htmlResult =
      s"""
      <div class="notification signature">
      <span class="message link"> <a href="$linkUrl">$linkText</a> </span>
      <span class="message by">$author</span>
      <span class="message time"><time>$time</time></span>
      </div>
      <div class="notification body article-content">$html</div>
      """

  }

  case class EventContent(
    linkUrl: String,
    linkText: String,
    event: String,
    author: String,
    time: ZonedDateTime
  ) extends NotificationContent {
    override def htmlResult =
      s"""
      <div class="notification signature">
      <span class="message link"> <a href="$linkUrl">$linkText</a> </span>
      <span class="message by">$author</span>
      $event this at <span class="message time"><time>$time</time></span>
      </div>
      """
  }

  case class TraySession(
    sessionStarted: ZonedDateTime, // used to decide when should be flush (reset) the session to perform a full update
    lastModified: Option[String], // lastModified HTTP headers used for notifications optimization
    lastPoll: ZonedDateTime, // last time when the user has polled notifications
    currentMesages: Seq[Notification],
    lastComments: Map[String, Seq[NotificationContent]], // we use URL for identification, as this is sure to be stable for an issue
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
        throw HttpErrorException(400, s"Invalid IP address $addr")
    }
  }

  private def isAdminSync(): Boolean = {
    Main.checkAdminAuthorized(userAuth.login)
  }

  def name = syncResponse {
    val isAdmin = isAdminSync()
    (userAuth.login, userAuth.fullName, if (isAdmin) "admin" else "user")
  }

  def addUser(userName: String) = syncResponse {
    Main.authorizedAdmin(userAuth.login)
    Storage.store(FileStore.FullName("users", userName), "user")
  }

  private def listUsersSync(ipAddress: String, state: String, requests: Boolean = false) = {
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
    Presence.listUsers(userAuth.login, requests)
  }

  def listUsers(ipAddress: String, state: String) = syncResponse {
    listUsersSync(ipAddress, state, true)
  }


  def requestWatching(user: String) = syncResponse {
    Presence.requestWatching(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def stopWatching(user: String) = syncResponse {
    Presence.stopWatching(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def allowWatchingMe(user: String) = syncResponse {
    Presence.allowWatchingMe(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def disallowWatchingMe(user: String) = syncResponse {
    Presence.disallowWatchingMe(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def trayUsersHTML(ipAddress: String, state: String) = syncResponse {

    val us = listUsersSync(ipAddress, state)

    val table = common.UserState.userTable(userAuth.login, state, us)

    def getUserStatusIcon(state: String) = {
      s"<img class='state icon' src='static/small/user-$state.png'></img>"
    }

    def displayTime(t: ZonedDateTime) = {
      s"<time>$t</time>"
    }

    def userRowHTML(row: common.model.UserRow) = {
      //language=HTML
      s"""<tr data-user="${row.login}">
           <td>${getUserStatusIcon(row.currentState)}</td>
           <td class="username"><a href="https://www.github.com/${row.login}">${row.login}</a></td>
           <td>${row.location}</td>
           <td>${if (row.currentState != "online" && row.currentState != "busy") displayTime(row.lastTime) else ""}</td>
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
      Presence.listUsers(userAuth.login, true)
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
    val sttpBackend = new SttpBackendAsyncWrapper(HttpURLConnectionBackend())(executeNow)
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
        per_page = 20 // default 50 is too high, often leads to timeouts
        //all = true
      ).at(executeNow).transform {
        case Success(response) =>
          val (newUnread, read) = response.data.partition(_.unread)

          val oldUnread = recentSession.filter(_.lastModified.nonEmpty).map(_.currentMesages).getOrElse(Seq.empty)
          // cannot compare Notification object, does not have a value based equals
          val newUnreadIds = newUnread.map(_.subject.url).toSet
          val readIds = read.map(_.subject.url).toSet

          // when a notification with the same id is issues again, it means the topic was updated
          // and we need to re-download the content
          val keepUnread = oldUnread.filterNot(n => newUnreadIds.contains(n.subject.url) || readIds.contains(n.subject.url))
          // remove any message reported as read (I do not think this ever happens, but if it would, we would handle it)
          val unread = newUnread ++ keepUnread
          val unreadIds = unread.map(_.subject.url).toSet

          println(s"${userAuth.login}: since $ifModifiedSince new: ${newUnread.size}, read: ${read.size}, unread: ${unread.size}")
          // download last comments for the new content
          val newComments = newUnread.flatMap {
            case n if n.subject.`type` == "Issue" =>
              // the issue may have no comments
              import rest.github.EnhancedRestImplicits._

              // if there is a comment, the only reason why we need to get the issue is to get its number (we need it for the link)
              val issueResponse = Try(gitHubAPI.request[Issue](n.subject.url, userAuth.token).awaitNow).toOption
              //val since = response.headers.lastModified.map(ZonedDateTime.parse(_, DateTimeFormatter.RFC_1123_DATE_TIME))
              for (issue <- issueResponse) yield {
                println(s"issue ${n.repository.full_name} ${issue.number}")
                // n.subject.latest_comment_url is sometimes the same as n.subject.url even if some comments exist
                // this happens e.g. with a state change (issue closed)
                val prefix = s"https://www.github.com/${n.repository.full_name}/issues/${issue.number}"

                def buildCommentContent(linkUrl: String, linkText: String, by: String, time: ZonedDateTime, body: String) = {
                  val context = n.repository.full_name
                  val markdown = gitHubAPI.api.authorized("Bearer " + userAuth.token).markdown.markdown(body, mode = "gfm", context = context).awaitNow
                  CommentContent(
                    linkUrl,
                    linkText,
                    HTMLUtils.xhtml(markdown.data), by, time
                  )
                }

                def issueData = buildCommentContent(prefix, s"#${issue.number}", issue.user.login, issue.updated_at, issue.body)

                def buildCommentData(c: Comment, relIndex: Int) = {
                  buildCommentContent(s"$prefix#issuecomment-${c.id}", s"#${issue.number}(-$relIndex)", c.user.login, c.updated_at, c.body)
                }
                def buildCommentDataSeq(cs: Seq[Comment]) = {
                  cs.zipWithIndex.map { case (c, index) => buildCommentData(c, cs.size - index) }
                }
                def buildEventData(c: Event) = {
                  EventContent(
                    prefix,
                    s"#${issue.number}",
                    c.event, c.actor.login, c.created_at
                  )
                }
                def buildEventDataSeq(es: Seq[Event]) = {
                  es.map(buildEventData)
                }

                val comments = Try {

                  val issuesAPI = gitHubAPI.api.authorized("Bearer " + userAuth.token)
                    .repos(n.repository.owner.login, n.repository.name)
                    .issuesAPI(issue.number)
                  val commentsSince = issuesAPI
                    .comments(since = n.last_read_at)
                    .awaitNow
                    .data
                  //println(s"commentsSince ${commentsSince.map(_.body).mkString("\n")}")
                  // skip anything before (and including) my last answer - if I have answered, I have probably read it
                  val commentsAfterMyAnswer = commentsSince
                    .reverse
                    .takeWhile(_.user.login != userAuth.login)
                    .reverse
                  //println(s"commentsAfterMyAnswer ${commentsAfterMyAnswer.map(_.body).mkString("\n")}")

                  if (commentsAfterMyAnswer.isEmpty) {
                    // if nothing is obtained this way, use the last comment
                    // if the last comment is the issue itself and the reason is a state change
                    // it means nothing was posted and we should display the state change itself

                    // a reasonable alternative could be to show no body at all in such situation
                    def fromLastCommentUrl = gitHubAPI.request[Comment](n.subject.latest_comment_url, userAuth.token).awaitNow
                    if (commentsSince.nonEmpty) buildCommentDataSeq(Seq(commentsSince.last))
                    else if (n.subject.latest_comment_url != n.subject.url) buildCommentDataSeq(Seq(fromLastCommentUrl))
                    else {
                      // try issue events first
                      val eventsSince = Try(issuesAPI.events(since = n.last_read_at).awaitNow).toOption.toSeq.flatMap(_.data)
                      if (eventsSince.nonEmpty) {
                        buildEventDataSeq(eventsSince)
                      } else {
                        // desperate fallback - obtain the very last comment for the issue, may require two more API calls
                        val comments = Try(issuesAPI.comments().awaitNow).toOption
                        val lastComment = comments.map { cs =>
                          cs.headers.paging.get("last").map { last =>
                            gitHubAPI.request[Seq[Comment]](last, userAuth.token).awaitNow.last
                          }.getOrElse(cs.data.last)
                        }.getOrElse(fromLastCommentUrl)
                        buildCommentDataSeq(Seq(lastComment))
                      }
                    }
                  } else buildCommentDataSeq(commentsAfterMyAnswer)

                }.toOption.toSeq.flatten

                val commentData = if (comments.nonEmpty) {
                  val showIssue = if ((n.last_read_at == null || issue.created_at >= n.last_read_at) && issue.user.login != userAuth.login) {
                    Seq(issueData)
                  } else Seq.empty
                  showIssue ++ comments
                } else Seq(issueData)

                n.subject.url -> commentData
              }
            case n if n.subject.`type` == "Release" =>
              import rest.github.EnhancedRestImplicits._
              val response = Try(gitHubAPI.request[Release](n.subject.url, userAuth.token).awaitNow).toOption
              for (release <- response) yield {
                n.subject.url -> Seq(CommentContent(
                  release.html_url,
                  s"${release.tag_name}",
                  release.body,
                  release.author.login,
                  release.published_at
                ))
              }
            case n if n.subject.`type` == "CheckSuite" =>
              None
            case n if n.subject.`type` == "Commit" =>
              import rest.github.EnhancedRestImplicits._
              val response = Try(gitHubAPI.request[Commit](n.subject.url, userAuth.token).awaitNow).toOption
              for (release <- response) yield {
                n.subject.url -> Seq(CommentContent(
                  release.html_url,
                  s"${release.sha}",
                  "",
                  release.author.login,
                  n.updated_at
                ))
              }
            case _ =>
              None
          }

          // remove comments for the messages we no longer display
          // map(identity) is a workaround for https://github.com/scala/bug/issues/6654
          val comments = recentSession.map(_.lastComments.filterKeys(unreadIds.contains).map(identity)).getOrElse(Map.empty) ++ newComments

          // response content seems inconsistent. Sometimes it contains messages
          def notificationHTML(n: common.model.github.Notification, comments: Map[String, Seq[NotificationContent]]) = {
            //language=HTML
            val comment = comments.get(n.subject.url)
            val header = s"""
            <div class="notification header">

              <span class="message title">${n.subject.title}</span><br/>
              ${n.repository.full_name}
              <span class="message time"><time>${n.updated_at}</time></span>
              <span class="message reason ${n.reason}">${n.reason}</span>
             </div>
             """
            comments.get(n.subject.url).map { cs =>
              header + cs.map(_.htmlResult).mkString("\n")
            }.getOrElse(header)
          }

          val notificationsTable =
          //language=HTML
            s"""<html>
              <head>
              <link href="static/tray.css" rel="stylesheet" />
              <link href="rest/issues.css" rel="stylesheet" />
              </head>
              <body class="notifications">
                <p><a href="https://www.github.com/notifications">GitHub notifications</a></p>
                <div class="notification table">
                ${unread.map("<div class='notification item'>" + notificationHTML(_, comments) + "</div>").mkString}
                </div>
              </body>
             </html>
            """

          // is it really sorted by updated_at, or some other (internal) update timestamp (e.g. when last_read_at is higher than updated_at?)
          val mostRecentNotified = recentSession.flatMap(_.mostRecentNotified)
          val newMostRecentNotified = unread.headOption.map(_.updated_at)
          // avoid flooding the notification area in case the user has many notifications
          // reverse to display oldest first
          import common.Util._
          val notifyUserAbout = mostRecentNotified.map { notifyFrom =>
            // check newUnread only - old unread were already reported if necessary
            newUnread.filter(_.updated_at > notifyFrom).tap { u =>
              println(s"Take notified from $notifyFrom: ${u.size}, unread times: ${newUnread.map(_.updated_at)}")
            }
            // beware: two messages might have identical timestamps. Unlikely, but possible
            // such message may have a notification skipped
          }.getOrElse(newUnread)

          val notifyUser = if (notifyUserAbout.lengthCompare(2) >= 0) Seq("New GitHub notifications")
          else notifyUserAbout.headOption.toSeq.map(_.subject.title)

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
