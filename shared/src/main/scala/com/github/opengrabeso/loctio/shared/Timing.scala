package com.github.opengrabeso.loctio.shared

trait Timing {
  def logTime(msg: =>String)
}

object Timing {
  class Real extends Timing {
    def now() = System.currentTimeMillis()
    val start = now()
    def logTime(msg: =>String) = println(s"$msg: time ${now() - start}")
  }

  class Dummy extends Timing {

    def logTime(msg: =>String) = {}

  }

  def start(log: Boolean = true): Timing = if (log) new Real else new Dummy
}
