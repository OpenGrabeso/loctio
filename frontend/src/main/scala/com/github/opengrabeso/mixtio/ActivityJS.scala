package com.github.opengrabeso.mixtio

import scala.scalajs.js
import scala.scalajs.js.annotation._

/**
facade for the JS provided in the [[com.github.opengrabeso.mixtio.requests.ActivityRequestHandler.activityJS]]
*/
@JSGlobalScope
@js.native
object ActivityJS extends js.Any {
  def actIdName(): String = js.native
  def activityEvents(): AnyRef = js.native

  var id: String = js.native

  var events: js.Array[js.Array[String]] = js.native

  var onEventsChanged: js.Function0[Unit] = js.native

  var map: js.Dynamic = js.native
}
