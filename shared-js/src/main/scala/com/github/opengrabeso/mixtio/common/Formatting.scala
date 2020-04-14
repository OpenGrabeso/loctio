package com.github.opengrabeso.mixtio.common

trait Formatting {

  def displaySeconds(duration: Int): String = {
    val hours = duration / 3600
    val secondsInHours = duration - hours * 3600
    val minutes = secondsInHours / 60
    val seconds = secondsInHours - minutes * 60
    if (hours > 0) {
      f"$hours:$minutes%02d:$seconds%02d"
    } else {
      f"$minutes:$seconds%02d"
    }
  }
}

object Formatting extends Formatting
