package com.github.opengrabeso.loctio

object UserActivity {

  def secondsSinceLastActivity(): Long = {
    if (Win32IdleTime.isSuppported) {
      Win32IdleTime.getIdleTimeMillisWin32 / 1000
    } else {
      MouseIdleTime.secondsSinceLastActivity()
    }
  }

  def start(): Unit = {
    if (Win32IdleTime.isSuppported) {
    } else {
      MouseIdleTime.start()
    }
  }
}
