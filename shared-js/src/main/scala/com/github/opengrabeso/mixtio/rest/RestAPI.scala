package com.github.opengrabeso.mixtio
package rest

import java.time.ZonedDateTime

import io.udash.rest._

import scala.concurrent.Future

trait RestAPI {

  @GET
  def identity(@Path in: String): Future[String]

  /* Caution: cookie parameters are used from the dom.document when called from Scala.js */
  def user(token: String): UserRestAPI

  @GET
  def now: Future[ZonedDateTime]
}

object RestAPI extends RestApiCompanion[EnhancedRestImplicits,RestAPI](EnhancedRestImplicits) {
  final val apiVersion = "1.0"
}
