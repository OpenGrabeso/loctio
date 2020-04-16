package com.github.opengrabeso.loctio
package rest

import io.udash.rest.raw.HttpErrorException

class UserRestAPIServer(val userAuth: Main.GitHubAuthResult) extends UserRestAPI with RestAPIUtils {
  private def checkState(state: String) = {
    state match {
      case "online" | "offline" | "busy" | "away" =>
      case _ =>
        throw HttpErrorException(400, s"Unknown state $state")
    }
  }

  private def checkLoginName(name: String) = {
    val Valid = "[a-z\\d](?:[a-z\\d]|-(?=[a-z\\d])){0,38}".r // see https://github.com/shinnn/github-username-regex
    name match {
      case Valid() =>
      case _ =>
        throw HttpErrorException(400, s"Invalid user name $name")
    }
  }

  private def checkIpAddress(addr: String) = {
    val Valid = "[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+".r
    addr match {
      case Valid() =>
      case _ =>
        throw HttpErrorException(400, s"Invalid user name $name")
    }
  }


  def name = syncResponse {
    (userAuth.login, userAuth.fullName)
  }

  def listUsers(ipAddress: String, state: String) = syncResponse {
    checkState(state)
    checkIpAddress(ipAddress)
    if (state != "away") {
      // when the user is away, do not update his presence
      if (state == "offline") {
        // when going offline, report only when we were not offline yet
        // this is important esp. for invisible, which will continue calling listUsers
        if (Presence.getUser(userAuth.login).forall(_.state != "offline")) {
          Presence.reportUser(userAuth.login, ipAddress, state)
        }
      }
      else {
        Presence.reportUser(userAuth.login, ipAddress, state)
      }
    }
    Presence.listUsers
  }

  def setLocationName(login: String, name: String) = syncResponse {
    checkLoginName(name)
    // check last ip address for the user
    Presence.getUser(login).map { presence =>
      Locations.nameLocation(presence.ipAddress, name)
      Presence.listUsers
    }.getOrElse(throw HttpErrorException(500, "User presence not found"))
  }


}
