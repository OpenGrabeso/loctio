package com.github.opengrabeso.loctio
package common.model.github

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

case class ThreadSubscription(
  subscribed: Boolean,
  ignored: Boolean,
  reason: String,
  created_at: ZonedDateTime,
  url: String,
  thread_url: String
)

object ThreadSubscription extends EnhancedRestDataCompanion[ThreadSubscription]
