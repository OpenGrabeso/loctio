package com.github.opengrabeso.loctio
package rest

import com.avsystem.commons.serialization
import com.avsystem.commons.serialization.{GenCodec, Input}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.time.temporal.ChronoUnit
import com.avsystem.commons.serialization.json.{JsonStringInput, JsonStringOutput}
import com.softwaremill.sttp.HttpURLConnectionBackend
import common.FileStore
import common.model._
import com.github.opengrabeso.github
import com.github.opengrabeso.github.{RestAPIClient => GitHubAPIClient}
import com.github.opengrabeso.github.model._
import com.github.opengrabeso.github.rest.ZonedDateTimeCodecs
import io.udash.rest.raw.HttpErrorException

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import shared.FutureAt._
import common.ChainingSyntax._
import common.Util._
import scalatags.Text.all._

import scala.collection.JavaConverters._

object UserRestAPIServer extends ZonedDateTimeCodecs {
  sealed trait NotificationContent {
    def htmlResult: String
  }

  object NotificationContent {
    implicit val codec: GenCodec[NotificationContent] = GenCodec.materialize
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

  /** event contains a specific message */

  case class SpecificEventContent(
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
      $event <span class="message time"><time>$time</time></span>
      </div>
      """
  }

  /** no need to store whole Issue / PR - it could be quite long */
  case class IssueOrPullRef(
    state: String,
    title: String,
    html_url: String,
    number: Long
  )

  object IssueOrPullRef {
    def from(i: IssueOrPull): IssueOrPullRef = IssueOrPullRef(
      state = i.state,
      title = i.title,
      html_url = i.html_url,
      number = i.number
    )

    implicit val codec: GenCodec[IssueOrPullRef] = GenCodec.materialize
  }

  case class TraySession(
    sessionStarted: ZonedDateTime, // used to decide when should be flush (reset) the session to perform a full update
    lastModified: Option[String], // lastModified HTTP headers used for notifications optimization
    lastPoll: ZonedDateTime, // last time when the user has polled notifications
    currentMesages: Seq[Notification] = Seq.empty,
    lastComments: Map[String, Seq[NotificationContent]] = Map.empty, // we use URL for identification, as this is sure to be stable for an issue
    info: Map[String, String] = Map.empty, // HTML used to display the info
    mostRecentNotified: Option[ZonedDateTime] = None,
    toReview: Seq[IssueOrPullRef] = Seq.empty, // list of PRs to be reviewed by the user (or his team)
    gitHubStatusReported: String = "none" // most recent github status displayed to the user
  )

  object TraySession {
    implicit val codec: GenCodec[TraySession] = GenCodec.materialize
  }
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

  private def currentUserSettings: Option[UserSettings] = {
    val json = Storage.load[String](FileStore.FullName("settings", userAuth.login))
    json.map(JsonStringInput.read[UserSettings](_))
  }

  def settings = syncResponse {
    currentUserSettings.getOrElse {
      throw HttpErrorException(404, "No settings for the user yet")
    }
  }

  def settings(s: UserSettings) = syncResponse {
    Storage.store(FileStore.FullName("settings", userAuth.login), JsonStringOutput.write(s))
  }

  def listAllTimezones = syncResponse {

    object ZoneComparator extends Ordering[ZoneId] {
      override def compare(zoneId1: ZoneId, zoneId2: ZoneId) = {
        val now = LocalDateTime.now
        val offset1 = now.atZone(zoneId1).getOffset
        val offset2 = now.atZone(zoneId2).getOffset
        val r = offset1.compareTo(offset2)
        if (r != 0) r
        else zoneId1.getId.compareTo(zoneId2.getId)
      }
    }

    ZoneId.getAvailableZoneIds.asScala.toSeq
      .map(ZoneId.of)
      .sorted(ZoneComparator)
      .map(_.getId)
  }

  def addUser(userName: String) = syncResponse {
    Main.authorizedAdmin(userAuth.login)
    if (Main.checkAdminAuthorized(userName)) {
      throw HttpErrorException(400, "Cannot add admin as an ordinary user")
    }
    if (Main.checkUserAuthorized(userName)) {
      throw HttpErrorException(400, "User already exists")
    }
    Storage.store(FileStore.FullName("users", userName), "user")
    Presence.listUsers(userAuth.login, true)
  }

  private def checkAutoInvisible(settings: UserSettings) = {
    val zone = Try(ZoneId.of(settings.timezone)).getOrElse(ZoneId.of("UTC"))
    val localNow = LocalDateTime.now(zone)


    val localMinutes = localNow.getHour * 60 + localNow.getMinute
    val visibleFromMinutes = settings.visibleHoursFrom * 60 + settings.visibleMinutesFrom
    val visibleToMinutes = settings.visibleHoursTo * 60 + settings.visibleMinutesTo

    //println(s"Local now $localNow for $zone $localMinutes in $visibleFromMinutes..$visibleToMinutes")

    localMinutes < visibleFromMinutes || localMinutes > visibleToMinutes
  }

  private def listUsersSync(ipAddress: String, realState: String, requests: Boolean = false) = {
    val settings = currentUserSettings.getOrElse(UserSettings())

    val forceInvisible = checkAutoInvisible(settings)

    val state = (forceInvisible, realState) match {
      case (true, "online" | "busy" | "away") =>
        "invisible"
      case _ =>
        realState
    }

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

  private def differentUserRequired(user: String): Unit = {
    if (userAuth.login == user) throw HttpErrorException(400, "Only different users can be watched")
  }

  def requestWatching(user: String) = syncResponse {
    differentUserRequired(user)
    Presence.requestWatching(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def stopWatching(user: String) = syncResponse {
    differentUserRequired(user)
    Presence.stopWatching(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def allowWatchingMe(user: String) = syncResponse {
    differentUserRequired(user)
    Presence.allowWatchingMe(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def disallowWatchingMe(user: String) = syncResponse {
    differentUserRequired(user)
    Presence.disallowWatchingMe(userAuth.login, user)
    Presence.listUsers(userAuth.login, true)
  }

  def trayUsersHTML(ipAddress: String, state: String) = syncResponse {

    val us = listUsersSync(ipAddress, state)

    val table = common.UserState.userTable(userAuth.login, state, us)

    def getUserStatusIcon(state: String) = {
      s"<img class='state icon' src='static/small/user-$state.png'/>"
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
          case "away" => "⦿"
          case "busy" => "⚫"
          case _ /*"offline"*/ => "⦾"
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
    trayNotificationsHTMLImpl(Storage)
  }

  def trayNotificationsHTMLImpl(storage: common.FileStore): (String, Seq[String], Int) = {


    def issueRefLink(issue: IssueOrPullRef): String = {
      val icon = issue.state match {
        case "closed" => issueStateIcon("issue-closed")
        case "open" => issueStateIcon("issue-opened")
        case _ => ""
      }

      s"""$icon<a href="${issue.html_url}">${issueRefTitle(issue)}</a> """
    }

    def issueLink(issue: IssueOrPull): String = {
      val icon = issue.state match {
        case "closed" => issueStateIcon("issue-closed")
        case "open" => issueStateIcon("issue-opened")
        case _ => ""
      }

      s"""$icon<a href="${issue.html_url}">${issueTitle(issue)}</a> """
    }

    def issueRefTitle(issue: IssueOrPullRef): String = {
      s"#${issue.number}"
    }

    def issueTitle(issue: IssueOrPull): String = {
      s"#${issue.number}"
    }

    def shaUrl(repo: Repository, sha: String) = s"""https://github.com/${repo.full_name}/commit/$sha"""

    def shaLink(repo: Repository, branch: String, sha: String) = {
      s"""<a href="${shaUrl(repo, sha)}">${repo.full_name}/$branch/${sha.take(12)}</a>"""
    }

    def issueStateIcon(iconName: String): String = {
      s"""<img class="issue-state-icon" src="/static/small/$iconName.png"/>"""
    }

    def successIcon(state: String): String = {
      state match {
        case "failure" =>
          issueStateIcon("x")
        case "success" =>
          issueStateIcon("check")
        case _ =>
          ""
      }
    }

    def buildNotificationHeader(n: Notification, prefix: String = ""): String = {
      //language=HTML
      s"""
            <div class="notification header">
              $prefix<span class="message title">${n.subject.title}</span><br/>
              ${n.repository.full_name}
              <span class="message time"><time>${n.updated_at}</time></span>
              <span class="message reason ${n.reason}">${n.reason}</span>
             </div>
             """
    }

    def buildStatusHeader(n: Notification, title: String, message: String): String = {
      //language=HTML
      s"""
            <div class="notification header">
              $title<br/>
              ${n.repository.full_name}
              <span class="message time"><time>${n.updated_at}</time></span>
              <span class="message reason ${n.reason}">${n.reason}</span>
             </div>
             """ + (if (message.nonEmpty) s"""<div class="notification body">$message</div>""" else "")
    }




    val sttpBackend = new SttpBackendAsyncWrapper(HttpURLConnectionBackend())(executeNow)
    try {

      val gitHubAPI = new GitHubAPIClient[github.rest.RestAPI](sttpBackend, "https://api.github.com")
      val gitHubStatusAPI = new GitHubAPIClient[github.rest.status.GitHubStatusAPI](sttpBackend, "https://www.githubstatus.com/api/v2")

      val session = try {
        storage.load[TraySession](sessionFilename)
      } catch {
        case ex: Exception =>
          println(s"Error loading TraySession: $ex")
          ex.printStackTrace()
          None
      }
      val now = ZonedDateTime.now()

      // heuristics to handle missing shutdown
      // user should be polling notification frequently (about once per minute).
      val recentSession = session.filter { s =>
        ChronoUnit.MINUTES.between(s.lastPoll, now) < 5 && // Long pause probably means the client was terminated and should get a fresh state
        ChronoUnit.MINUTES.between(s.sessionStarted, now) < 120 // each two hours perform a full reset
      }

      val ifModifiedSince = recentSession.flatMap(_.lastModified).orNull

      val statusMessageFuture = gitHubStatusAPI.api.status
      //println(s"notifications.get ${userAuth.login}: since $ifModifiedSince")
      // we expect status to return very quickly
      val statusMessageResult = Try(Await.result(statusMessageFuture, Duration(10, SECONDS)))
      val statusMessageToReport = statusMessageResult.toOption.map(_.status.indicator).getOrElse("exception")

      val statusMessageText = if (statusMessageToReport != "none") {
        statusMessageResult match {
          case Success(status) =>
            Some {
              p(
                a(href := "https://www.githubstatus.com/", "GitHub Status"),
                ": ",
                b(status.status.description)
              )
            }
          case Failure(ex) =>
            Some {
              p(
                a(
                  href := "https://www.githubstatus.com/",
                  "GitHub Status"
                ),
                b(s" is down ($ex)")
              )
            }
        }
      } else None

      val statusMessageNotification = if (session.map(_.gitHubStatusReported).getOrElse("none") != statusMessageToReport) {
        println(s"Github status notification: $statusMessageToReport")
        statusMessageResult match {
          case Success(s) =>
            Some(s"GitHub: ${s.status.description}")
          case Failure(ex) =>
            Some("githubstatus.com is down")
        }
      } else None

      // TODO: with Java 11 we might be able to use real futures?

      println(s"Request ${userAuth.login}: since $ifModifiedSince")

      val api = gitHubAPI.api.authorized("Bearer " + userAuth.token)
      var prList = if (ifModifiedSince == null) {
        // full status check - get list of PRs, check which of them require a review
        val prResults = api.search.issues(s"is:pull-request is:open review-requested:${userAuth.login}").at(executeNow).transform {
          case Success(results) =>
            Success(results.data.items.map(IssueOrPullRef.from))
          case Failure(_) =>
            // TODO: process failures
            Success(Seq.empty)
        }
        Await.result(prResults, Duration.Inf)
      } else {
        session.map(_.toReview).getOrElse(Seq.empty)
      }

      val prText = if (prList.nonEmpty) {
        """<div class="notification table">""" + prList.map { pr =>
          //language=HTML
          s"""
                <div class='notification item'>
                <div class="notification header">
                  ${issueRefLink(pr)}<span class="message title">${pr.title}</span> <span class="message reason review_requested">review requested</span><br/>
                 </div>
                  </div>
                 """
        }.mkString("<br/>") + "</div><hr/>"
      } else {
        ""
      }
      val r = api.notifications.get(
        ifModifiedSince,
        per_page = 20 // default 50 is too high, often leads to timeouts
        //all = true
      ).at(executeNow).transform {
        case Success(response) =>
          val (newUnread, read) = response.data.partition(_.unread)

          val oldUnread = recentSession.filter(_.lastModified.nonEmpty).map(_.currentMesages).getOrElse(Seq.empty)
          // cannot compare Notification object, does not have a value based equals
          val newUnreadIds = newUnread.map(_.id).toSet
          val readIds = read.map(_.id).toSet

          // when a notification with the same id is issues again, it means the topic was updated
          // and we need to re-download the content
          val keepUnread = oldUnread.filterNot(n => newUnreadIds.contains(n.id) || readIds.contains(n.id))
          // remove any message reported as read (I do not think this ever happens, but if it would, we would handle it)
          val unread = newUnread ++ keepUnread
          val unreadIds = unread.map(_.id).toSet

          println(s"${userAuth.login}: since $ifModifiedSince / last-mod ${response.headers.lastModified} new: ${newUnread.size}, read: ${read.size}, unread: ${unread.size}")
          // download last comments for the new content

          def buildIssueNotificationHeader(n: Notification, i: IssueOrPull): String = buildNotificationHeader(n, issueLink(i))

          val newComments = newUnread.flatMap {
            case n if n.subject.`type` == "Issue" || n.subject.`type` == "PullRequest" =>
              // the issue may have no comments
              import github.rest.EnhancedRestImplicits._

              // if there is a comment, the only reason why we need to get the issue is to get its number (we need it for the link)
              val issueResponse = if (n.subject.`type` == "PullRequest") {
                Try(gitHubAPI.request[Pull](n.subject.url, userAuth.token).awaitNow).toOption.map { prResponse =>
                  if (
                    !prList.exists(_.html_url == prResponse.html_url)
                      && prResponse.state == "open"
                      && (
                        n.reason == "review_requested" && prResponse.requested_teams.nonEmpty // TODO: check team membership
                          || prResponse.requested_reviewers.exists(_.login == userAuth.login) // if requested personally, we do not care what event it was
                      )
                  ) {
                    prList = prList :+ IssueOrPullRef.from(prResponse)
                  }
                  prResponse
                }
              } else Try(gitHubAPI.request[Issue](n.subject.url, userAuth.token).awaitNow).toOption
              //val since = response.headers.lastModified.map(ZonedDateTime.parse(_, DateTimeFormatter.RFC_1123_DATE_TIME))

              for (issue <- issueResponse) yield {
                println(s"issue ${n.repository.full_name} ${issue.number}")
                // n.subject.latest_comment_url is sometimes the same as n.subject.url even if some comments exist
                // this happens e.g. with a state change (issue closed)
                val prefix = issue.html_url

                def buildCommentContent(linkUrl: String, linkText: String, by: String, time: ZonedDateTime, body: String) = {
                  val context = n.repository.full_name
                  val markdown = api.markdown.markdown(body, mode = "gfm", context = context).awaitNow
                  CommentContent(
                    linkUrl,
                    linkText,
                    HTMLUtils.xhtml(markdown.data), by, time
                  )
                }

                def issueData = buildCommentContent(prefix, issueTitle(issue), issue.user.login, issue.updated_at, issue.body)

                def buildCommentData(c: Comment, relIndex: Int) = {
                  buildCommentContent(s"$prefix#issuecomment-${c.id}", s"#${issue.number}(-$relIndex)", c.user.login, c.updated_at, c.body)
                }
                def buildCommentDataSeq(cs: Seq[Comment]) = {
                  cs.zipWithIndex.map { case (c, index) => buildCommentData(c, cs.size - index) }
                }
                def buildEventData(c: Event) = {
                  c.event match {
                    case "review_requested" =>
                      SpecificEventContent(prefix, issueTitle(issue), "requested review at", c.actor.login, c.created_at)
                    case "review_request_removed" =>
                      SpecificEventContent(prefix, issueTitle(issue), "removed review request at", c.actor.login, c.created_at)
                    case "mentioned" =>
                      SpecificEventContent(prefix, issueTitle(issue), "was mentioned at", c.actor.login, c.created_at)
                    case e =>
                      EventContent(prefix, issueTitle(issue), e, c.actor.login, c.created_at)
                  }
                }

                implicit class OnlyLast(es: Seq[Event]) {
                  def onlyLast(predicate: Event => Boolean): Seq[Event] = {
                    val last = es.lastIndexWhere(predicate)
                    if (last <= 0) es
                    else {
                      val (to, from) = es.splitAt(last)
                      to.filterNot(predicate) ++ from
                    }
                  }
                  def onlyLastType(types: String*): Seq[Event] = onlyLast(e => types.contains(e.event))
                }

                def buildEventDataSeq(es: Seq[Event]) = {
                  // display only the last event in some categories - we are not interested in the complete history
                  es
                    .onlyLastType("reopened", "closed")
                    .onlyLastType("review_requested", "review_request_removed")
                    .onlyLastType("mentioned")
                    .onlyLastType("assigned", "unassigned")
                    .onlyLastType("subscribed", "unsubscribed")
                    .onlyLastType("head_ref_force_pushed", "head_ref_deleted", "head_ref_restored")
                    .map(buildEventData)
                }

                val comments = Try {

                  val issuesAPI = api
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

                (
                  n.id,
                  commentData,
                  Some(buildIssueNotificationHeader(n, issue))
                )

              }
            case n if n.subject.`type` == "Release" =>
              import github.rest.EnhancedRestImplicits._
              val response = Try(gitHubAPI.request[Release](n.subject.url, userAuth.token).awaitNow).toOption
              for (release <- response) yield (
                n.id,
                Seq(CommentContent(
                  release.html_url,
                  s"${release.tag_name}",
                  release.body,
                  release.author.login,
                  release.published_at
                )),
                None
              )

            case n if n.subject.`type` == "CheckSuite" =>
              val ExtractBranch = "(.*)( workflow .* for )([^ ]+)( branch.*)".r
              n.subject.title match {
                case ExtractBranch(workflow, infix, branch, postfix) =>

                  Try(api.repos(n.repository.owner.login, n.repository.name).commits(branch).checkRuns(status = "completed", filter = "all").awaitNow).toOption.flatMap {checkRuns =>
                    // note: checkRuns will always list the latest check run, use a timestamp guess to find the most likely run
                    val possible = checkRuns.check_runs.filter(_.completed_at <= n.updated_at)
                    val failed = possible.filter(_.conclusion != "success")

                    def queryParEncode(q: String) = java.net.URLEncoder.encode(q, "UTF-8")
                    val workflowLink = a(
                      href := s"""https://github.com/${n.repository.full_name}/actions?query=${queryParEncode(s"""workflow:"$workflow"""")}""",
                      workflow
                    ).render

                    val title = s"""<span class="message title">$workflowLink$infix$branch$postfix</span>"""

                    failed.headOption.orElse(possible.headOption).map {run =>
                      val message =
                        s"""
                           ${successIcon(run.conclusion)}
                           Run: <a href="${run.html_url}">${workflow}</a><br/>
                           Commit: ${shaLink(n.repository, branch, run.head_sha)}<br/>
                         """

                      (n.id, Seq.empty, Some(buildStatusHeader(n, title, message)))
                    }.orElse(Some{
                      (n.id, Seq.empty, Some(buildStatusHeader(n, title, "")))
                    })
                  }

                case _ =>
                  None
              }

            case n if n.subject.`type` == "Commit" =>
              import github.rest.EnhancedRestImplicits._
              val response = Try(gitHubAPI.request[Commit](n.subject.url, userAuth.token).awaitNow).toOption
              for (release <- response) yield {
                (
                  n.id,
                  Seq(CommentContent(
                    release.html_url,
                    s"${release.sha}",
                    "",
                    release.author.login,
                    n.updated_at
                  )),
                  None
                )
              }
            case _ =>
              None
          }

          // remove comments for the messages we no longer display
          // map(identity) is a workaround for https://github.com/scala/bug/issues/6654
          val comments = recentSession.map(_.lastComments.filterKeys(unreadIds.contains).map(identity)).getOrElse(Map.empty) ++ newComments.map(c => c._1 -> c._2)
          val infos = recentSession.map(_.info.filterKeys(unreadIds.contains).map(identity)).getOrElse(Map.empty) ++ newComments.flatMap(c => c._3.map(c3 => c._1 -> c3))

          def notificationHTML(n: Notification, comments: Map[String, Seq[NotificationContent]], infos: Map[String, String]) = {
            val header = infos.getOrElse(n.id, buildNotificationHeader(n))
            comments.get(n.id).map { cs =>
              header + cs.map(_.htmlResult).mkString("\n")
            }.getOrElse(header)
          }

          val notificationsTable = html(
            head(
              link(href := "static/tray.css", rel := "stylesheet"),
              link(href := "rest/issues.css", rel := "stylesheet"),
            ),
            body(
              cls := "notifications",
              p(
                a(href := "https://www.github.com/notifications", "GitHub notifications")
              ),
              raw(prText),
              div(
                statusMessageText
              ),
              div(
                cls := "notification table",
                raw(unread.map("<div class='notification item'>" + notificationHTML(_, comments, infos) + "</div>").mkString)
              )

            )
          ).render

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
            // if there is no lastModified in the response headers, synthesize a sensible value, as null has a special meaning and causes api.search to be used
            response.headers.lastModified.orElse(Some(now.minusSeconds(60).toString)),
            now,
            unread,
            comments,
            infos,
            newMostRecentNotified,
            toReview = prList,
            gitHubStatusReported = statusMessageToReport
          )
          storage.store(sessionFilename, newSession)

          val nextAfter = response.headers.xPollInterval.map(_.toInt).getOrElse(60)

          println(s"${userAuth.login}: store lastModified ${newSession.lastModified}")

          Success(notificationsTable, notifyUser ++ statusMessageNotification, nextAfter)
        case Failure(github.rest.DataWithHeaders.HttpErrorExceptionWithHeaders(ex, headers)) =>

          if (ex.code != 304 ) {
            println(s"HTTP error code ${ex.code} cause ${ex.payload.getOrElse("")}")
          }
          val nextAfter = headers.xPollInterval.map(_.toInt).getOrElse(60)

          val newSession = recentSession.map {
            _.copy(
              lastPoll = now,
              gitHubStatusReported = statusMessageToReport
            )
          }.getOrElse {
            // we need to store something, but there is no session recorded and GitHub returned an error
            // create some empty placeholder
            TraySession(
              sessionStarted = now,
              lastModified = None,
              lastPoll = now,
              gitHubStatusReported = statusMessageToReport
            )
          }
          // update the session info to keep alive (prevent resetting)
          storage.store(sessionFilename, newSession)

          val errorMessage = ex.payload.toOption.filter(_.nonEmpty).map(payload => s": $payload").getOrElse("")

          val errorReport = if (ex.code != 304) {
            // TODO: display notification (on first error only)
            html(
              head(
                link(href := "static/tray.css", rel := "stylesheet"),
                link(href := "rest/issues.css", rel := "stylesheet"),
              ),
              body(
                cls := "notifications",
                p(
                  b(s"GitHub Error ${ex.code}"),
                  errorMessage
                )
              )
            ).render
          } else ""

          Success(errorReport, statusMessageNotification.toSeq, nextAfter)
        case Failure(ex) =>
          println(s"Failure $ex")
          Failure(ex)
      }
      // it would be nice to pass Future directly, but somehow it does not work - probably some Google App Engine limitation
      val ret = try {
        Await.result(r, Duration(60, SECONDS))
      } catch {
        case ex: Exception =>
          println("tray notification request failed")
          ex.printStackTrace()
          throw ex
      }
      ret
      //      trayNotificationsImpl(sttpBackend)
    } finally {
      sttpBackend.close()
    }
  }
}
