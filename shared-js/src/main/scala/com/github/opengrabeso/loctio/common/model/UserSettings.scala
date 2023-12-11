package com.github.opengrabeso.loctio
package common
package model

import rest.EnhancedRestDataCompanion
import io.udash.properties.ModelPropertyCreator

case class UserSettings(
  visibleHoursFrom: Int = 0,
  visibleMinutesFrom: Int = 0,
  visibleHoursTo: Int = 24,
  visibleMinutesTo: Int = 0,
  timezone: String = "UTC",
  displayLocation: Boolean = true
)

object UserSettings extends EnhancedRestDataCompanion[UserSettings] {
  implicit val modelPropertyCreator: ModelPropertyCreator[UserSettings] = ModelPropertyCreator.materialize[UserSettings]
}
