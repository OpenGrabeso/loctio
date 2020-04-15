package com.github.opengrabeso.loctio
package rest

class UserRestAPIServer(val userAuth: Main.GitHubAuthResult) extends UserRestAPI with RestAPIUtils {
  def name = syncResponse {
    (userAuth.login, userAuth.fullName)
  }

  def report(ipAddress: String) = syncResponse {
    Presence.reportUser(userAuth.login, ipAddress)
  }

  def listUsers = syncResponse {
    // make sure the user making the query is always reported as online
    // note: we do not have his ip address, therefore we cannot change the location yet,
    Presence.reportUser(userAuth.login)
    Presence.listUsers
  }

}
