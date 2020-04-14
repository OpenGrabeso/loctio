package com.github.opengrabeso.loctio
package rest

import java.time.{Instant, ZoneId, ZoneOffset, ZonedDateTime}

import com.avsystem.commons.serialization.whenAbsent
import common.model._
import io.udash.rest._
import io.udash.rest.raw.HttpBody

import scala.concurrent.Future

trait UserRestAPI {

  @GET
  def name: Future[String]
}

object UserRestAPI extends RestApiCompanion[EnhancedRestImplicits,UserRestAPI](EnhancedRestImplicits)