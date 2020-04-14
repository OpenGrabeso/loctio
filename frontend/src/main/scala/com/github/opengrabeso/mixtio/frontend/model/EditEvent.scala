package com.github.opengrabeso.mixtio
package frontend.model

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import common.model._
import io.udash.HasModelPropertyCreator

case class EditEvent(
  action: String, event: Event, time: Int, dist: Double, active: Boolean = true,
  uploading: Boolean = false, uploadState: String = "",
  uploadId: Option[String] = None, // upload progress id
  strava: Option[FileId.StravaId] = None // upload result
) {
  def boundary: Boolean = action.startsWith("split")
  def shouldBeUploaded: Boolean = boundary && active
}

object EditEvent extends HasModelPropertyCreator[EditEvent] {
  def apply(startTime: ZonedDateTime, e: Event, dist: Double): EditEvent = {
    new EditEvent(
      e.defaultEvent, e, ChronoUnit.SECONDS.between(startTime, e.stamp).toInt, dist
    )
  }
}
