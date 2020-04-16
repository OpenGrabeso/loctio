package com.github.opengrabeso.loctio

import java.time.{ZoneOffset, ZonedDateTime}

import Storage._
import common.model._

import common.FileStore.FullName

object Presence {
  @SerialVersionUID(10L)
  case class PresenceInfo(
    ipAddress: String,
    lastSeen: ZonedDateTime
  ) extends Serializable

  def reportUser(login: String, ipAddress: String): Unit = {
    val now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
    val info = PresenceInfo(ipAddress, now)
    store(FullName("presence", login), info)
  }

  def reportUser(login: String): Unit = {
    val fullName = FullName("presence", login)
    for (current <- load[PresenceInfo](fullName)) {
      val now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
      store(fullName, current.copy(lastSeen = now))
    }
  }

  def getUserIpAddress(login: String): Option[String] = {
    load[PresenceInfo](FullName("presence", login)).map(_.ipAddress)
  }

  def listUsers: Seq[(String, LocationInfo)] = {
    val items = enumerate("presence/")
    items.map(i => i._2 -> load[PresenceInfo](i._1)).flatMap { case (login, data) =>
      // we need to transfer the time as UTC, otherwise the JS client is unable to decode it
      data.map(d => login -> LocationInfo(Locations.locationFromIpAddress(d.ipAddress), d.lastSeen.withZoneSameInstant(ZoneOffset.UTC)))
    }
  }.toSeq
}
