package com.github.opengrabeso.mixtio
package frontend
package views.settings_base

import dataModel.SettingsModel
import io.udash._

trait SettingsFactory {
  def loadSettings(model: ModelProperty[SettingsModel], userService: services.UserContextService): Unit = {
  }

}
