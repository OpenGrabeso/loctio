package com.github.opengrabeso.loctio
package common.model.github

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

// https://developer.github.com/v3/repos/statuses/#get-the-combined-status-for-a-specific-ref

case class CombinedStatus(
  state: String,
  sha: String,
  statuses: Seq[Status],
  total_count: Int,
  repository: Repository,
  commit_url: String,
  url: String
)

object CombinedStatus extends EnhancedRestDataCompanion[CombinedStatus]
