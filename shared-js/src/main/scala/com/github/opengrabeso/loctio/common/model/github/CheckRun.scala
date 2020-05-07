package com.github.opengrabeso.loctio
package common.model.github

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

case class Output (
  title: String,
  summary: String,
  text: String,
  annotations_count: Int,
  annotations_url: String
)

object Output extends EnhancedRestDataCompanion[Output]

// https://developer.github.com/v3/checks/runs/#list-check-runs-for-a-git-reference
case class CheckRun(
  id: Long,
  head_sha: String,
  url: String,
  html_url: String,
  details_url: String,
  status: String,
  conclusion: String,
  output: Output,
  started_at: ZonedDateTime,
  completed_at: ZonedDateTime,
  name: String,
)

object CheckRun extends EnhancedRestDataCompanion[CheckRun]