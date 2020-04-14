package com.github.opengrabeso.mixtio
package common.model

/** It would be possible to implement SportId using com.avsystem.commons.misc.NamedEnum, but this is incompatible with
  * default Java serialization. As long as Java serialization is used in the old Mixtio storage, we rather
  * provide a custom codec for Scala enumeration
  * */
object SportId extends Enumeration {
  // https://strava.github.io/api/v3/uploads/
  //   ride, run, swim, workout, hike, walk, nordicski, alpineski, backcountryski, iceskate, inlineskate, kitesurf,
  //   rollerski, windsurf, workout, snowboard, snowshoe, ebikeride, virtualride

  // order by priority, roughly fastest to slowest (prefer faster sport does less harm on segments)
  // Workout (as Unknown) is the last option
  final val Ride, Run, Hike, Walk, Swim, NordicSki, AlpineSki, IceSkate, InlineSkate, KiteSurf,
    RollerSki, WindSurf, Canoeing, Kayaking, Rowing, Surfing, Snowboard, Snowshoe, EbikeRide, VirtualRide, Workout: Value = Value
  type SportId = Value
}
