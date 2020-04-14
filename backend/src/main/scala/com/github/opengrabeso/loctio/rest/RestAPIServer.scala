package com.github.opengrabeso.loctio
package rest

import Main._
import java.time.ZonedDateTime
import io.udash.rest.raw.HttpErrorException

object RestAPIServer extends RestAPI with RestAPIUtils {

  def identity(in: String) = {
    syncResponse(in)
  }

  def createUser(auth: GitHubAuthResult): GitHubAuthResult = {
    auth
  }

  def user(token: String): UserRestAPI = {
    // TODO: check token revocation
    val logging = true
    if (logging) println(s"Try userAPI for token $token")
    val fullName = Storage.FullName("login", token)
    val auth = Storage.load[GitHubAuthResult](fullName).map { a =>
      if (a.token == token) {
        if (logging) println(s"Get userAPI for user ${a.login}")
        a
      } else {
        throw HttpErrorException(401, "Provided auth code '$authCode' does not match the one stored on the server")
      }
    }.getOrElse {
      val auth = Main.gitHubAuth(token)
      if (logging) println(s"Create userAPI for user ${auth.login}")
      Storage.store(fullName, auth)
      auth
    }
    new UserRestAPIServer(auth)
  }

  def now = syncResponse {
    ZonedDateTime.now()
  }
}
