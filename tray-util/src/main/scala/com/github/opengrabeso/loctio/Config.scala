package com.github.opengrabeso.loctio

import java.util.prefs.Preferences

case class Config(token: String)

object Config {
  private val node = Preferences.userNodeForPackage(this.getClass)

  def load: Config = {
    val token = node.get("token", "")
    Config(token)
  }

  def store(cfg: Config): Unit = {
    node.put("token", cfg.token)
  }

}
