package com.github.opengrabeso.loctio
package common

import java.time.temporal.ChronoUnit
import java.time.{Duration, ZonedDateTime}

import model._

object UserState {
  def getEffectiveUserStatus(state: String, time: ZonedDateTime) = {
    val now = ZonedDateTime.now()
    val age = Duration.between(time, now).toMinutes
    val displayState = state match {
      case  "online" | "busy" =>
        if (age < 5) {
          state
        } else if (age < 60) {
          "away"
        } else {
          "offline"
        }
      case _ =>
        // if user is reported as offline, do not check if the user was active recently
        // as we got a positive notification about going offline
        // note: invisible user is reporting offline as well
        state
    }
    displayState
  }

  /*
   we expect platform specific callbacks to format time components - ZonedTimeTime formatting not supported on Scala.js
   */
  def smartTime(
    t: ZonedDateTime, formatTime: ZonedDateTime => String,
    formatDate: ZonedDateTime => String,
    formatDayOfWeek: ZonedDateTime => String
  ): String = {
    def roundTime(t: ZonedDateTime) = {
      val roundOffset = (t.getMinute + 7)  / 15 * 15 - t.getMinute
      t.plusMinutes(roundOffset)
    }
    val now = ZonedDateTime.now()
    val since = t.until(now, ChronoUnit.MINUTES)
    val daysSince = ChronoUnit.DAYS.between(t.toLocalDate, now.toLocalDate)
    if (since < 10) "5 min ago"
    else if (since < 60) s"${since / 10 * 10} min ago"
    else if (daysSince == 0) formatTime(roundTime(t))
    else if (daysSince < 7) {
      formatDayOfWeek(t) + " " + formatTime(roundTime(t))
    } else {
      formatDate(t)
    }
  }

  def smartAbsoluteTime(
    t: ZonedDateTime, formatTime: ZonedDateTime => String,
    formatDate: ZonedDateTime => String,
    formatDayOfWeek: ZonedDateTime => String
  ): String = {
    val now = ZonedDateTime.now()
    val daysSince = ChronoUnit.DAYS.between(t.toLocalDate, now.toLocalDate)
    if (daysSince == 0) formatTime(t)
    else if (daysSince < 7) {
      formatDayOfWeek(t) + " " + formatTime(t)
    } else {
      formatDate(t) + " " + formatTime(t)
    }
  }

  def userTable(currentUser: String, currentUserState: String, value: Seq[(String, LocationInfo)]) = {
    def userLowerThan(a: UserRow, b: UserRow): Boolean = {
      def userGroup(a: UserRow) = {
        if (a.login == currentUser) 0 // current user first
        else a.currentState match {
          case "online" | "busy" | "away" =>
            1
          case "offline" =>
            2
          case "unknown" =>
            3
          case _ =>
            1000 // something unexpected, list last
        }
      }
      val aLevel = userGroup(a)
      val bLevel = userGroup(b)
      if (aLevel < bLevel) true
      else if (aLevel == bLevel) a.login < b.login // sort alphabetically in the same group
      else false
    }

    //println(s"Users:\n${value.mkString("\n")}")
    value.map { u =>
      if (u._1 == currentUser) {
        UserRow(u._1, u._2.location, u._2.lastSeen, currentUserState)
      } else {
        UserRow(u._1, u._2.location, u._2.lastSeen, getEffectiveUserStatus(u._2.state, u._2.lastSeen))
      }
    }.sortWith(userLowerThan)

  }

}
