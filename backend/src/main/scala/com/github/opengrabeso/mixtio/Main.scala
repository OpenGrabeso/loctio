package com.github.opengrabeso.mixtio

import java.util.Properties

import com.fasterxml.jackson.databind.JsonNode
import com.google.api.client.http.{GenericUrl, HttpHeaders}
import io.udash.rest.raw.HttpErrorException

object Main extends common.Formatting {

  import RequestUtils._

  case class SecretResult(users: Set[String], error: String)

  def secret: SecretResult = {
    val filename = "/secret.txt"
    try {
      val secretStream = Main.getClass.getResourceAsStream(filename)
      val lines = scala.io.Source.fromInputStream(secretStream).getLines
      val usersLine = lines.next()
      val users = usersLine.split(",").map(_.trim)
      SecretResult(users.toSet, "")
    } catch {
      case _: NullPointerException => // no file found
        SecretResult(Set.empty, s"Missing $filename, app developer should check README.md")
      case _: Exception =>
        SecretResult(Set.empty, s"Bad $filename, app developer should check README.md")
    }
  }

  def devMode: Boolean = {
    val prop = new Properties()
    prop.load(getClass.getResourceAsStream("/config.properties"))
    prop.getProperty("devMode").toBoolean
  }

  case class GitHubAuthResult(token: String, login: String, fullName: String) {
    // userId used for serialization, needs to be stable, cannot be created from a token
    lazy val userId: String = login
    def displayName: String = if (fullName.isEmpty) login else fullName
  }

  private def authRequest(token: String): JsonNode = {
    val request = requestFactory.buildGetRequest(new GenericUrl("https://api.github.com/user"))
    val headers = if (request.getHeaders != null) request.getHeaders else request.setHeaders(new HttpHeaders).getHeaders
    headers.setAuthorization("Bearer " + token)
    val response = request.execute() // TODO: async?

    jsonMapper.readTree(response.getContent)
  }

  def gitHubAuth(token: String): GitHubAuthResult = {

    val SecretResult(users, _) = secret

    val responseJson = authRequest(token)

    val login = responseJson.path("login").textValue
    val name = responseJson.path("name").textValue

    if (users.contains(login)) {

      val auth = GitHubAuthResult(token, login, name)
      rest.RestAPIServer.createUser(auth)
      auth
    } else {
      throw HttpErrorException(403, "Contact server administrator to get the access")
    }
  }

}





