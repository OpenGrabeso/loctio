package com.github.opengrabeso.loctio

import java.awt.MouseInfo

import scala.concurrent.duration
import scala.concurrent.duration.Duration

/*
Simple portable (cross-platform) alternative to Win32IdleTime
* */


object MouseIdleTime {
  import scala.concurrent.ExecutionContext.Implicits.global

  private def now() = System.currentTimeMillis()

  def secondsSinceLastActivity(): Long = {
    (now() - lastActivity) / 1000
  }

  case class WatchedState(x: Int, y: Int)

  private def currentState(): WatchedState = {
    val loc = MouseInfo.getPointerInfo.getLocation
    WatchedState(loc.x, loc.y)
  }

  @volatile private var lastActivity = now()

  private var lastState: WatchedState = currentState()

  Start.system.scheduler.scheduleAtFixedRate(Duration(5, duration.SECONDS), Duration(5, duration.SECONDS)) { () =>
    val s = currentState()
    synchronized {
      if (s != lastState) {
        val t = now()
        lastState = s
        lastActivity = t
      }
      if (false) {
        val since = secondsSinceLastActivity()
        if (since >= 10) {
          println(s"User inactive for $since s")
        }
      }
    }
  }

  def start(): Unit = {
    // called just to initialize object member variables
  }

}
