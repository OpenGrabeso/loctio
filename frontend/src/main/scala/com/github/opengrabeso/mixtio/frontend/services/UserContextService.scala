package com.github.opengrabeso.mixtio
package frontend
package services

import common.model._

import scala.concurrent.{ExecutionContext, Future}
import UserContextService._

object UserContextService {
  class UserContextData(userId: String, token: String, rpc: rest.RestAPI)(implicit ec: ExecutionContext) {
    var context = UserContext(userId, token)
  }
}

class UserContextService(rpc: rest.RestAPI)(implicit ec: ExecutionContext) {

  private var userData: Option[UserContextData] = None

  def login(userId: String, token: String): UserContext = {
    println(s"Login user $userId")
    val ctx = new UserContextData(userId, token, rpc)
    userData = Some(ctx)
    ctx.context
  }

  def userName: Option[Future[String]] = api.map(_.name)
  def userId: Option[String] = userData.map(_.context.userId)

  def api: Option[rest.UserRestAPI] = userData.map { data =>
    rpc.user(data.context.token)
  }
}
