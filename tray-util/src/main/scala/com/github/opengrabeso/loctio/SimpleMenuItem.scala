package com.github.opengrabeso.loctio

import scala.swing._

class SimpleMenuItem (title: String, callback: => Unit) extends MenuItem(
  new Action(title) {
    override def apply() = callback
  }
)


