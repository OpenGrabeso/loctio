package com.github.opengrabeso.loctio
package rest

import Main._
import java.time.ZonedDateTime

import com.github.opengrabeso.loctio.common.model.CssData
import io.udash.css.CssStringRenderer
import scalacss.internal.{Renderer, StringRenderer}

object RestAPIServer extends RestAPI with RestAPIUtils {

  def identity(in: String) = {
    syncResponse(in)
  }

  def createUser(auth: GitHubAuthResult): GitHubAuthResult = {
    auth
  }

  def user(token: String): UserRestAPIServer = {
    // TODO: check token revocation
    val logging = false
    if (logging) println(s"Try userAPI for token $token")
    val fullName = common.FileStore.FullName("login", token)
    val auth = Storage.load[GitHubAuthResult](fullName).map { a =>
      assert(a.token == token)
      authorized(a.login)
      if (logging) println(s"Get userAPI for user ${a.login}")
      a
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


  private lazy val issuesCssRendered: String = {
    import common.css._
    val styles = Seq(IssueStyles)
    implicit val renderer: Renderer[String] = StringRenderer.defaultPretty
    new CssStringRenderer(styles).render()
  }

  def issuesCss() = syncResponse {
    CssData(issuesCssRendered)
  }

}
