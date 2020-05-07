package com.github.opengrabeso.loctio
package common.model.github

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

case class Status(
  url: String,
  avatar_url: String,
  id: String,
  state: String,
  description: String,
  context: String,
  created_at: ZonedDateTime,
  updated_at: ZonedDateTime,
)

object Status extends EnhancedRestDataCompanion[Status]
