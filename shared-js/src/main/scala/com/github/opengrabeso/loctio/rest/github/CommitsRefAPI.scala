package com.github.opengrabeso.loctio
package rest.github

import com.avsystem.commons.serialization.transientDefault
import io.udash.rest._
import common.model.github._

import scala.concurrent.Future

trait CommitsRefAPI {
  @GET
  def status: Future[CombinedStatus]

  // https://developer.github.com/v3/repos/statuses/#list-statuses-for-a-specific-ref
  @GET
  def statuses(
    @transientDefault page: Int = 0,
    @transientDefault per_page: Int = 0,
  ): Future[Seq[Status]]

  // https://developer.github.com/v3/checks/runs/#list-check-runs-for-a-git-reference
  @GET("check-runs")
  def checkRuns(
    @Header("Accept") accept: String = "application/vnd.github.antiope-preview+json",
    @transientDefault check_name: String = null, // Returns check runs with the specified name
    @transientDefault status: String = null, // Returns check runs with the specified status. Can be one of queued, in_progress, or completed.
    @transientDefault filter: String = null, // Filters check runs by their completed_at timestamp. Can be one of latest (returning the most recent check runs) or all. Default: latest
  ): Future[CheckRuns]
}

object CommitsRefAPI extends RestClientApiCompanion[EnhancedRestImplicits,CommitsRefAPI](EnhancedRestImplicits)

