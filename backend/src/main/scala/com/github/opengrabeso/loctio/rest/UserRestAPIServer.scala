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
    Presence.listUsers
  }

}
