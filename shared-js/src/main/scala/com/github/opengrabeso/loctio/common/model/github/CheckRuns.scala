package com.github.opengrabeso.loctio
package common.model.github

import rest.EnhancedRestDataCompanion

// https://developer.github.com/v3/checks/runs/#list-check-runs-for-a-git-reference
case class CheckRuns(
  total_count: Long,
  check_runs: Seq[CheckRun]
)

object CheckRuns extends EnhancedRestDataCompanion[CheckRuns]