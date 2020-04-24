package com.github.opengrabeso.loctio
package rest.github

import com.avsystem.commons.serialization.transientDefault
import java.time.ZonedDateTime

import scala.concurrent.Future
import common.model.github._
import io.udash.rest._

trait IssuesAPI {
  @GET("")
  def get: Future[Issue]

  @PATCH("")
  def update(
    title: String,
    body: String,
    state: String,
    @transientDefault milestone: Int = -1,
    labels: Array[String],
    assignees: Array[String]
  ): Future[Issue]

  @GET
  def comments(
    @transientDefault since: ZonedDateTime = null,
    @transientDefault page: Int = 0,
    @transientDefault per_page: Int = 0,
  ): Future[DataWithHeaders[Seq[Comment]]]

  @POST("comments")
  def createComment(body: String): Future[Comment]
}

object IssuesAPI extends RestClientApiCompanion[EnhancedRestImplicits,IssuesAPI](EnhancedRestImplicits)
