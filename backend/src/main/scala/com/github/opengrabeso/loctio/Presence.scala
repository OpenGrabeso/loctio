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

  def listUsers: Seq[(String, LocationInfo)] = {
    val now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
    val items = enumerate("state/")
    items.map(i => i._2 -> load[PresenceInfo](i._1)).flatMap { case (login, data) =>
      // we need to transfer the time as UTC, otherwise the JS client is unable to decode it
      data.map { d =>
        val lastSeen = d.lastSeen.withZoneSameInstant(ZoneOffset.UTC)
        val age = Duration.between(lastSeen, now).getSeconds
        // when seen in a last minute, always report as online
        // when one client has reported going offline, there still may be other clients running
        val state = if (age < 70) "online" else d.state
        //println(s"Report $login as $d")
        login -> LocationInfo(Locations.locationFromIpAddress(d.ipAddress), lastSeen, state)
      }
    }
  }.toSeq
}
