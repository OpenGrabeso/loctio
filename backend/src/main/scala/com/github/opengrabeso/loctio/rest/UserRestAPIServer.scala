package com.github.opengrabeso.loctio
package rest

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.softwaremill.sttp.HttpURLConnectionBackend
import common.model._
import io.udash.rest.raw.HttpErrorException

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.global

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
      s"<img class='state icon' src='static/user-$state.ico'></img>"
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

  def shutdown(data: RestString) = syncResponse {
    println(s"Received ${userAuth.login} shutdown ${data.value}")
    // value other then now can be used for testing and debugging the protocol
    if (data.value == "now") {
      Presence.getUser(userAuth.login).filter(_.state != "offline").foreach { p =>
        Presence.reportUser(userAuth.login, p.ipAddress, "offline")
      }
    }
  }

  def trayNotificationsHTML() = syncResponse {
    val sttpBackend = HttpURLConnectionBackend()
    try {
      val gitHubAPI = new GitHubAPIClient(sttpBackend)
      implicit val ec = createEC() // avoid thread pool, we are responsible for shutting down any threads we have created
      // TODO: handle and pass ifModifiedSince, store and update user state

      val r = gitHubAPI.api.authorized("Bearer " + userAuth.token).notifications.get().transform {
        case Success(response) =>

          val ns = response.data
          def notificationHTML(n: common.model.github.Notification) = {
            //language=HTML
            s"""
          <tr><td><span class="message title">${n.subject.title}</span><br/>
            ${n.repository.full_name} <span class="message time"><time>${n.updated_at}</time></span></td></tr>
           """
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
                ${ns.map(notificationHTML).mkString}
                </table>
              </body>
             </html>
          """

          // avoid flooding the notification area in case the user has many notifications
          // TODO: take(0) is only before ifModifiedSince is implemented
          val notifyUser = for (n <- ns.take(0).reverse) yield { // reverse to display oldest first
            n.subject.title
          }

          val nextAfter = response.headers.xPollInterval.map(_.toInt).getOrElse(60)
          Success(notificationsTable, notifyUser, nextAfter)
        case Failure(rest.github.DataWithHeaders.HttpErrorExceptionWithHeaders(ex, headers)) =>
          val nextAfter = headers.xPollInterval.map(_.toInt).getOrElse(60)
          Success("", Seq.empty, nextAfter)
        case Failure(ex) =>
          Failure(ex)
      }
      // it would be nice to pass Future directly, but somehow it does not work - probably some Google App Engine limitation
      Await.result(r, Duration(60, SECONDS))
    } finally {
      sttpBackend.close()
    }
  }


}
