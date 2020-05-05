package com.github.opengrabeso.loctio

import scala.io.Source

object Octicons {
  def loadResource(name: String): String = {
    val s = Source.fromResource(name)
    s.getLines.mkString("\n")
  }

  def loadIcon(name: String): String = {
    "/octicons/" + name + ".svg"
  }

}
