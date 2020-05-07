package com.github.opengrabeso.loctio
package rest.github

import com.avsystem.commons.serialization.transientDefault
import io.udash.rest._
import common.model.github._

import scala.concurrent.Future

trait CommitsStatusAPI {
  @GET
  def status: Future[CombinedStatus]

  // https://developer.github.com/v3/repos/statuses/#list-statuses-for-a-specific-ref
  @GET
  def statuses(
    @transientDefault page: Int = 0,
    @transientDefault per_page: Int = 0,
  ): Future[Seq[Status]]
}

object CommitsStatusAPI extends RestClientApiCompanion[EnhancedRestImplicits,CommitsStatusAPI](EnhancedRestImplicits)

