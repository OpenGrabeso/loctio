package com.github.opengrabeso.loctio
package common.model.github

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

case class Repository(
  name: String,
  full_name: String,
  owner: User,
  html_url: String,
  description: String,
  url: String
)

object Repository extends EnhancedRestDataCompanion[Repository]
