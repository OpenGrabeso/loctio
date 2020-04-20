package com.github.opengrabeso.loctio
package rest.github

import com.avsystem.commons.serialization.transientDefault

import scala.concurrent.Future
import common.model.github._
import io.udash.rest._

trait SubscriptionAPI {
  @GET("")
  def get: Future[ThreadSubscription]

  @PUT("")
  def set(ignored: Boolean): Future[Unit]

  @DELETE(path = "")
  def delete(): Future[Unit]
}

object SubscriptionAPI extends RestClientApiCompanion[EnhancedRestImplicits,SubscriptionAPI](EnhancedRestImplicits)
