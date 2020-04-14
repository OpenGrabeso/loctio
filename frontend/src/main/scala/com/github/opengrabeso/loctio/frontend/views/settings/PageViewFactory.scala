package com.github.opengrabeso.loctio
package frontend
package views
package settings

import java.time.ZonedDateTime

import dataModel.SettingsModel
import routing.{RoutingState, SettingsPageState}
import io.udash._

import scala.concurrent.Future

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService,
) extends ViewFactory[SettingsPageState.type] with settings_base.SettingsFactory {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[SettingsPageState.type]) = {
    val model = ModelProperty(PageModel(SettingsModel(null, null, null)))

    loadSettings(model.subModel(_.s), userService)

    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}