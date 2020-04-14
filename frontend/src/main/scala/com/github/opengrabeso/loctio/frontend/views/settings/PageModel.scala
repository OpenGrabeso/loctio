package com.github.opengrabeso.loctio
package frontend.views.settings

import dataModel.SettingsModel
import io.udash._

case class PageModel(s: SettingsModel)

object PageModel extends HasModelPropertyCreator[PageModel]
