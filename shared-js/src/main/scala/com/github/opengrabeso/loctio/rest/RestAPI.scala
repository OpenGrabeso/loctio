package com.github.opengrabeso.loctio
package rest

import java.time.ZonedDateTime

import common.model.CssData
import io.udash.rest._

import scala.concurrent.Future

trait RestAPI {

  @GET
  def identity(@Path in: String): Future[String]

  def user(token: String): UserRestAPI

  @GET
  def now: Future[ZonedDateTime]

  @GET("issues.css")
  def issuesCss(): Future[CssData]
}

object RestAPI extends RestApiCompanion[EnhancedRestImplicits,RestAPI](EnhancedRestImplicits) {
  final val apiVersion = "1.0"
}
