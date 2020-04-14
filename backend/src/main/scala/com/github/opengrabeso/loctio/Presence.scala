package com.github.opengrabeso.loctio

import java.time.ZonedDateTime

import Storage._
import common.model._

object Presence {
  @SerialVersionUID(10L)
  case class PresenceInfo(
    ipAddress: String,
    lastSeen: ZonedDateTime
  ) extends Serializable

  def reportUser(login: String, ipAddress: String): Unit = {
    val now = ZonedDateTime.now()
    val info = PresenceInfo(ipAddress, now)
    store(FullName("presence", login), info)
  }

  def locationFromIpAddress(ipAddress: String): String = {
    ipAddress.trim
  }

  def listUsers: Seq[(String, LocationInfo)] = {
    val items = enumerate("presence")
    items.map(i => i._2 -> load[PresenceInfo](i._1)).flatMap { case (login, data) =>
      data.map(d => login -> LocationInfo(locationFromIpAddress(d.ipAddress), d.lastSeen.toString))
    }
  }.toSeq
}
