package com.github.opengrabeso.loctio
package frontend
package views.settings_base

import dataModel.SettingsModel
import io.udash._

trait SettingsFactory {
  def loadSettings(model: ModelProperty[SettingsModel], userService: services.UserContextService): Unit = {
  }

}
