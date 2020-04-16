package com.github.opengrabeso.loctio
package rest

import io.udash.rest.raw.HttpErrorException

class UserRestAPIServer(val userAuth: Main.GitHubAuthResult) extends UserRestAPI with RestAPIUtils {
  def name = syncResponse {
    (userAuth.login, userAuth.fullName)
  }

  def report(ipAddress: String, state: String) = syncResponse {
    Presence.reportUser(userAuth.login, ipAddress, state)
  }

  def listUsers = syncResponse {
    // make sure the user making the query is always reported as online
    // note: we do not have his ip address, therefore we cannot change the location yet,
    Presence.reportUser(userAuth.login)
    Presence.listUsers
  }

  def setLocationName(login: String, name: String) = syncResponse {
    // check last ip address for the user
    Presence.getUserIpAddress(login).map { ipAddress =>
      Locations.nameLocation(ipAddress, name)
      Presence.reportUser(userAuth.login)
      Presence.listUsers
    }.getOrElse(throw HttpErrorException(500, "User presence not found"))
  }


}
