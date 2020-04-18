package com.github.opengrabeso.loctio
package common

import java.time.{Duration, ZonedDateTime}

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
      case "offline" if (age < 1) =>
        // if user is reported as offline recently, report as online, because another client may still report as online
        state
      case _ =>
        // if user is reported as offline, do not check if the user was active recently
        // as we got a positive notification about going offline
        // note: invisible user is reporting offline as well
        state
    }
    displayState
  }


}
