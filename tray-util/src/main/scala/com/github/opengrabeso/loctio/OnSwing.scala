package com.github.opengrabeso.loctio

import javax.swing.SwingUtilities

import scala.concurrent.ExecutionContext

object OnSwing extends ExecutionContext {
  def execute(runnable: Runnable) = {
    SwingUtilities.invokeLater(runnable)
  }
  def reportFailure(cause: Throwable) = {
    cause.printStackTrace()
  }
}
