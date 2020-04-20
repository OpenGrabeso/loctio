package com.github.opengrabeso.loctio.common

trait Formatting {
  def shortNameString(name: String, maxLen: Int = 30): String = {
    val ellipsis = "..."
    if (name.length < maxLen) name
    else {
      val allowed = name.take(maxLen-ellipsis.length)
      // prefer shortening about whole words
      val lastSeparator = allowed.lastIndexOf(' ')
      val used = if (lastSeparator >= allowed.length - 8) allowed.take(lastSeparator) else allowed
      used + ellipsis
    }
  }

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
