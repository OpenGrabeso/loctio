package com.github.opengrabeso.loctio

import java.time.{Duration, ZoneOffset, ZonedDateTime}
import Storage._
import com.avsystem.commons.serialization.{GenCodec, HasGenCodec}
import common.model._
import common.FileStore.FullName

object Presence {
  // contacts/xxx/watching contains list of users we want to watch
  // contacts/xxx/watched-by contains list of users that want to watch us, we allow it by storing true:java.lang.Boolean in the file

  private def pathWatching(login: String, user: String) = s"contacts/$login/watching/$user"
  private def pathWatchedBy(login: String, user: String) = s"contacts/$login/watched-by/$user"

  private def watchingPrefix(forUser: String) = s"contacts/$forUser/watching"
  private def watchedByPrefix(forUser: String) = s"contacts/$forUser/watched-by"

  def b(boolean: Boolean) = java.lang.Boolean.valueOf(boolean)

  def requestWatching(login: String, user: String): Unit = {
    val watching = loadUserList(watchingPrefix(login))
    if (!watching.contains(user)) {
      storeUserList(watchingPrefix(login), watching + (user -> false)) // value has no meaning
    }

    val userWatchedBy = loadUserList(watchedByPrefix(user))
    // if the request already exists, do not touch it, to prevent overwriting request which was allowed
    if (!userWatchedBy.contains(login)) {
      storeUserList(watchedByPrefix(user), userWatchedBy + (user -> false))
    }
  }

  def stopWatching(login: String, user: String): Unit = {
    val watching = loadUserList(watchingPrefix(login))
    if (watching.contains(user)) {
      storeUserList(watchingPrefix(login), watching - user)
    }

    val userWatchedBy = loadUserList(watchedByPrefix(user))
    // delete the entry only if it exists and contains false - we have requested watching, but it is still pending
    // the fact we stop watching does not mean the user has withdrawn their consent - delete only when not containing true
    if (userWatchedBy.get(login).contains(false)) {
      storeUserList(watchedByPrefix(login), userWatchedBy - login)
    }
  }

  def disallowWatchingMe(login: String, user: String): Unit = {
    val watchedBy = loadUserList(watchedByPrefix(login))
    if (watchedBy.get(user).contains(true)) {
      storeUserList(watchedByPrefix(login), watchedBy + (user -> false))  // the request still exists, only no longer contains true
    }
  }

  def allowWatchingMe(login: String, user: String): Unit = {
    val watchedBy = loadUserList(watchedByPrefix(login))
    if (!watchedBy.get(user).contains(true)) {
      storeUserList(watchedByPrefix(login), watchedBy + (user ->true))
    }
  }

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

  // implement list of users as a single readable entity to avoid storage.list, which is class A operation (more expensive)
  case class UserList(seq: Map[String, Boolean]) {
    def keys: Seq[String] = seq.keys.toSeq

    def + (kv: (String, Boolean)): UserList = copy(seq = seq + kv)
    def - (k: String): UserList = copy(seq = seq - k)
    def contains(k: String): Boolean = seq.contains(k)
    def get(k: String): Option[Boolean] = seq.get(k)
  }

  object UserList extends HasGenCodec[UserList]

  private def loadUserList(prefix: String) = {
    load[UserList](FullName(prefix + "-users")).getOrElse {
      // filter nonEmpty because folders created from the web console include an item with an empty name
      val files = enumerate(prefix + "/").toSeq.filter(_._2.nonEmpty)
      val content = files.map { f =>
        f._2 -> load[java.lang.Boolean](f._1).contains(true)
      }
      val converted = UserList(content.toMap)
      store(FullName(prefix + "-users"), converted)
      converted
    }
  }

  private def storeUserList(prefix: String, list: UserList): Unit = {
    Storage.store(FullName(prefix + "-users"), list)
  }

  def listUsers(forUser: String, requests: Boolean = false): Seq[(String, LocationInfo)] = {
    val now = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)

    val watching = loadUserList(watchingPrefix(forUser))
    // TODO: load watchedBy only when needed
    val watchedBy = loadUserList(watchedByPrefix(forUser))

    val (watchingAllowed, watchingRequested) = watching.keys.partition { user =>
      loadUserList(watchedByPrefix(user)).get(forUser).contains(true)
    }
    val (watchedByAllowed, watchedByRequested) = watchedBy.keys.partition { user =>
      watchedBy.get(user).contains(true)
    }

    def relation(listAllowed: Seq[String], listRequested: Seq[String], user: String): Relation = {
      if (listAllowed.contains(user)) Relation.Allowed
      else if (listRequested.contains(user)) Relation.Requested
      else Relation.No
    }

    val fullState = (forUser +: watchingAllowed)
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
          val watchedBy = relation(watchedByAllowed, watchedByRequested, login)
          login -> LocationInfo(Locations.locationFromIpAddress(d.ipAddress), lastSeen, state, Relation.Allowed, watchedBy)
        }.getOrElse(login -> LocationInfo("", now, "unknown", Relation.No, Relation.No))
      }
    if (requests) {

      def createUserInfo(user: String, state: String) = {
        val watching = relation(watchingAllowed, watchingRequested, user)
        val watchedBy = relation(watchedByAllowed, watchedByRequested, user)
        user -> LocationInfo(state, now, "unknown", watching, watchedBy)
      }

      // TODO: append those who want to watch us, but we are not watching them
      val partialState = ((watching.keys ++ watchedBy.keys).toSet -- watchingAllowed).toSeq.map(createUserInfo(_, ""))
      // TODO: merge states as necessary
      fullState ++ partialState
    } else fullState
  }
}
