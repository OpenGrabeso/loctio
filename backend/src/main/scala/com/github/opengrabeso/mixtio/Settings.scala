package com.github.opengrabeso.mixtio

object Settings {

  private def userSettings(userId: String) = {
    Storage.load[SettingsStorage](Storage.FullName(Main.namespace.settings, "settings", userId))
  }

  def store(userId: String, settings: SettingsStorage): Unit = {
    Storage.store(Storage.FullName(Main.namespace.settings, "settings", userId), settings)
  }

  def apply(userId: String): SettingsStorage = {
    userSettings(userId).getOrElse(SettingsStorage())
  }


}


