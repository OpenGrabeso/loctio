package com.github.opengrabeso.loctio
package rest.github

import com.avsystem.commons.serialization.transientDefault

import scala.concurrent.Future
import common.model.github._
import io.udash.rest._

trait ThreadsAPI {
  @GET("")
  def get: Future[Notification]

  @PATCH("")
  def markAsRead(): Future[Unit]

  def subscription(threadId: Long): SubscriptionAPI
}

object ThreadsAPI extends RestClientApiCompanion[EnhancedRestImplicits,ThreadsAPI](EnhancedRestImplicits)
