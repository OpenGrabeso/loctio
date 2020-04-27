package com.github.opengrabeso.loctio

import java.time.{Duration, ZoneOffset, ZonedDateTime}

import Storage._
import common.model._
import common.FileStore.FullName

object Presence {
  @SerialVersionUID(20L)
  case class PresenceInfo(
    ipAddress: String,
    lastSeen: ZonedDateTime,
    state: String
  ) extends Serializable

  def reportUser(login: String, ipAddress: String, state: String): Unit = {
    val now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
    val info = PresenceInfo(ipAddress, now, state)
    store(FullName("state", login), info)
  }

  def getUser(login: String): Option[PresenceInfo] = {
    load[PresenceInfo](FullName("state", login))
  }

  def listUsers(forUser: String, requests: Boolean = false): Seq[(String, LocationInfo)] = {
    val now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)

    // filter nonEmpty because folders created from the web console include an item with an empty name
    val watching = enumerate(s"contacts/$forUser/watching/").toSeq.map(_._2).filter(_.nonEmpty)
    val watchedBy = enumerate(s"contacts/$forUser/watched-by/").toSeq.map(_._2).filter(_.nonEmpty)

    val (watchingAllowed, watchingRequested) = watching.partition(user => enumerate(s"contacts/$user/watched-by").nonEmpty)

    val fullState = watchingAllowed
      .map(user => user -> load[PresenceInfo](FullName(s"state/$user")))
      .map { case (login, data) =>
        data.map { d =>
          // we need to transfer the time as UTC, otherwise the JS client is unable to decode it
          val lastSeen = d.lastSeen.withZoneSameInstant(ZoneOffset.UTC)
          val age = Duration.between(lastSeen, now).getSeconds
          // when seen in a last minute, always report as online
          // when one client has reported going offline, there still may be other clients running
          val state = if (age < 70 && d.state == "offline") "online" else d.state
          //println(s"Report $login as $d")
          login -> LocationInfo(Locations.locationFromIpAddress(d.ipAddress), lastSeen, state)
        }.getOrElse(login -> LocationInfo("", now, "unknown"))
      }
    if (requests) {
      val (watchedByAllowed, watchedByRequested) = watchedBy.partition(user => enumerate(s"contacts/$forUser/watched-by").nonEmpty)

      def createUserInfo(user: String, state: String) = {
        user -> LocationInfo(state, now, "unknown")
      }

      // TODO: append those who want to watch us, but we are not watching them
      // TODO: merge states as necessary
      fullState ++
        watchedByAllowed.diff(watchingAllowed).map(createUserInfo(_, "Watching me")) ++
        watchingRequested.diff(watchingAllowed).map(createUserInfo(_, "Watch request sent")) ++
        watchedByRequested.diff(watchingAllowed).map(createUserInfo(_, "Requesting to watch me"))

    } else fullState
  }
}
