package com.github.opengrabeso.loctio

import com.avsystem.commons.serialization.HasGenCodec

import java.util.Properties
import com.fasterxml.jackson.databind.JsonNode
import com.google.api.client.http.{GenericUrl, HttpHeaders, HttpResponseException}
import io.udash.rest.raw.{HttpBody, HttpErrorException}
import common.ChainingSyntax._

object Main extends common.Formatting {

  import RequestUtils._

  def devMode: Boolean = {
    Option(getClass.getResourceAsStream("/config.properties")).exists { is =>
      val prop = new Properties()
      prop.load(is)
      is.close()
      prop.getProperty("devMode").toBoolean
    }
  }

  case class GitHubAuthResult(token: String, login: String, fullName: String) {
    // userId used for serialization, needs to be stable, cannot be created from a token
    lazy val userId: String = login
    def displayName: String = if (fullName.isEmpty) login else fullName
  }

  object GitHubAuthResult extends HasGenCodec[GitHubAuthResult]

  private def authRequest(token: String): JsonNode = {
    try {
      val request = requestFactory.buildGetRequest(new GenericUrl("https://api.github.com/user"))
      val headers = if (request.getHeaders != null) request.getHeaders else request.setHeaders(new HttpHeaders).getHeaders
      headers.setAuthorization("Bearer " + token)
      val response = request.execute() // TODO: async?

      jsonMapper.readTree(response.getContent)
    } catch {
      case e: HttpResponseException if e.getStatusCode == 401 || e.getStatusCode == 403 =>
        throw HttpErrorException.plain(e.getStatusCode, e.getStatusMessage)
      case e: HttpResponseException =>
        println(s"Unexpected auth error $e")
        throw HttpErrorException.plain(e.getStatusCode, e.getStatusMessage)
      case ex: Exception =>
        throw HttpErrorException.plain(500, "Unexpected error when authenticating with GitHub")
    }
  }


  def checkUserAuthorized(login: String): Boolean = {
    Storage.exists(s"users/$login")
  }

  def checkAdminAuthorized(login: String): Boolean = {
    Storage.exists(s"admins/$login")
  }

  def authorized(login: String): Unit = {
    // admins are always authorized, no need to list them
    // check normal user list
    if (!checkUserAuthorized(login)) {
      if (!checkAdminAuthorized(login)) {
        throw HttpErrorException.plain(403, s"User $login not authorized. Contact server administrator to get the access")
      }
    }
  }

  def authorizedAdmin(login: String): Unit = {
    // admins are always authorized, no need to list them
    if (!checkAdminAuthorized(login)) {
      throw HttpErrorException.plain(403, s"Admin $login not authorized. Contact server administrator to get the access")
    }
  }

  def gitHubAuth(token: String): GitHubAuthResult = {
    val responseJson = authRequest(token)

    val login = responseJson.path("login").textValue
    val name = responseJson.path("name").textValue

    authorized(login)

    val auth = GitHubAuthResult(token, login, name)
    rest.RestAPIServer.createUser(auth)
  }

}





