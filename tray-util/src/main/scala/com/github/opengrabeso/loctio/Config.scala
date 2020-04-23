package com.github.opengrabeso.loctio

import java.util.prefs.Preferences

case class Config(token: String, state: String)

object Config {
  private val node = Preferences.userNodeForPackage(this.getClass)

  def empty: Config = Config("", "offline")
  def load: Config = {
    val token = node.get("token", "")
    // we currently reset to online each time the user is restarted
    Config(token, "online")
  }

  def store(cfg: Config): Unit = {
    node.put("token", cfg.token)
    node.put("state", cfg.state)
  }

}
