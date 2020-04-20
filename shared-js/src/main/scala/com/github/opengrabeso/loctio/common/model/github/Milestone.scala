package com.github.opengrabeso.loctio
package common.model.github

case class Milestone(
  id: Long,
  number: Int,
  title: String,
  description: String
)

import rest.EnhancedRestDataCompanion

object Milestone extends EnhancedRestDataCompanion[Milestone]

