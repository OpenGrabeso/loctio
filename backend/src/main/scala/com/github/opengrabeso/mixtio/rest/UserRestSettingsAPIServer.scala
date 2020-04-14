package com.github.opengrabeso.mixtio
package rest

import scala.concurrent.Future

class UserRestSettingsAPIServer(userId: String) extends UserRestSettingsAPI with RestAPIUtils {
  private def getIntSetting(f: SettingsStorage => Int) = syncResponse {
    f(Settings(userId))
  }
  private def setIntSetting(f: SettingsStorage => SettingsStorage): Future[Unit] = syncResponse {
    val original = Settings(userId)
    val changed = f(original)
    Settings.store(userId, changed)

  }

  def quest_time_offset = getIntSetting(_.questTimeOffset)
  def max_hr = getIntSetting(_.maxHR)
  def elev_filter = getIntSetting(_.elevFilter)

  def quest_time_offset(v: Int) = setIntSetting(_.setQuestTimeOffset(Some(v)))
  def max_hr(v: Int) = setIntSetting(_.setMaxHR(Some(v)))
  def elev_filter(v: Int) = setIntSetting(_.setElevFilter(Some(v)))
}
