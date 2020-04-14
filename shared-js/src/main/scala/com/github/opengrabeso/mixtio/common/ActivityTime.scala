package com.github.opengrabeso.mixtio
package common
import java.time.ZonedDateTime
import common.Util._
import model._

object ActivityTime {
  def alwaysIgnoreBeforeTime(stravaActivities: Seq[ZonedDateTime]): ZonedDateTime = {
    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeNow = ZonedDateTime.now() minusYears  2
    (Seq(ignoreBeforeNow) ++ stravaActivities.lastOption).max
  }

  def defaultIgnoreBeforeTime(stravaActivities: Seq[ZonedDateTime]): ZonedDateTime = {
    // ignore anything older than oldest of recent Strava activities
    val ignoreBeforeLast = alwaysIgnoreBeforeTime(stravaActivities)
    // ignore anything older than 14 days before most recent Strava activity
    val ignoreBeforeFirst = stravaActivities.headOption.map(_ minusDays  14)
    // ignore anything older than 2 months from now
    val ignoreBeforeNow = ZonedDateTime.now() minusMonths 2

    (Seq(ignoreBeforeNow, ignoreBeforeLast) ++ ignoreBeforeFirst).max
  }

  def alwaysIgnoreBefore(stravaActivities: Seq[ActivityId]): ZonedDateTime = {
    ActivityTime.alwaysIgnoreBeforeTime(stravaActivities.map(_.startTime))
  }

  def defaultIgnoreBefore(stravaActivities: Seq[ActivityId]): ZonedDateTime = {
    ActivityTime.defaultIgnoreBeforeTime(stravaActivities.map(_.startTime))
  }


}
