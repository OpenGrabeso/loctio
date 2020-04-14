package com.github.opengrabeso.mixtio
package common.model

import java.time.temporal.ChronoUnit

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

import common.Util._
import SportId._

@SerialVersionUID(11L)
case class ActivityId(id: FileId, digest: String, name: String, startTime: ZonedDateTime, endTime: ZonedDateTime, sportName: SportId, distance: Double) {

  override def toString = s"$startTime..$endTime: '${id.toString}' - '$name'"

  def secondsInActivity(time: ZonedDateTime): Int = ChronoUnit.SECONDS.between(startTime, time).toInt
  def timeInActivity(seconds: Int): ZonedDateTime = startTime.plusSeconds(seconds)

  val duration: Int = ChronoUnit.SECONDS.between(startTime, endTime).toInt

  def timeOffset(offset: Int): ActivityId = copy(startTime = startTime plusSeconds offset, endTime = endTime plusSeconds offset)

  def isMatching(that: ActivityId): Boolean = {
    // check overlap time
    val commonBeg = Seq(startTime,that.startTime).max
    val commonEnd = Seq(endTime,that.endTime).min
    if (commonEnd > commonBeg) {
      val commonDuration = ChronoUnit.SECONDS.between(commonBeg, commonEnd)
      commonDuration > (duration min that.duration) * 0.75f
    } else false
  }

  def isMatchingExactly(that: ActivityId, maxError: Double = 0.01): Boolean = {
    def secondsBetween(a: ZonedDateTime, b: ZonedDateTime) = ChronoUnit.SECONDS.between(a, b)
    val commonBeg = Seq(startTime,that.startTime).max
    val commonEnd = Seq(endTime,that.endTime).min
    if (commonEnd > commonBeg) {
      val commonDuration = secondsBetween(commonBeg, commonEnd)
      val maxAbsError = commonDuration * maxError
      secondsBetween(startTime, that.startTime).abs < maxAbsError && secondsBetween(endTime, that.endTime).abs < maxAbsError
    } else false
  }

  def link: String = {
    id match {
      case FileId.StravaId(num) =>
        s"https://www.strava.com/activities/$num"
      case _ =>
        null // not a Strava activity - no link
    }
  }


  def shortName: String = {
    common.Formatting.shortNameString(name)
  }
}

object ActivityId extends EnhancedRestDataCompanion[ActivityId]