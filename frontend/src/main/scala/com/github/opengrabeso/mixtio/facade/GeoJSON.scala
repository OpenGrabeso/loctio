package com.github.opengrabeso.mixtio.facade

import mapboxgl.LngLat

import scala.scalajs.js
import scala.scalajs.js.annotation._

@JSGlobalScope
@js.native
object GeoJSON extends js.Any {
  @js.native
  trait Geometry extends js.Any {
    val coordinates: LngLat = js.native // TODO: introduce LngLatLike instead
  }

  @js.native
  trait Feature extends js.Any {
    val `type`: String = js.native // always "Feature"
    val properties: js.Dynamic = js.native
    val geometry: Geometry = js.native
  }
}
