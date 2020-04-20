package com.github.opengrabeso.loctio
package rest.github

import common.model.github._
import io.udash.rest._

import scala.concurrent.Future

trait AuthorizedAPI {
  @GET
  def user: Future[User]

  def repos(owner: String, repo: String): RepositoryAPI


  def notifications: NotificationsAPI

  @Prefix("")
  def markdown: MarkdownAPI

  @GET
  def rate_limit: Future[RateLimits]
}

object AuthorizedAPI extends RestClientApiCompanion[EnhancedRestImplicits,AuthorizedAPI](EnhancedRestImplicits)