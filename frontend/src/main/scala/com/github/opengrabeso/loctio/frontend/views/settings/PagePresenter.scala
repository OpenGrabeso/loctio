package com.github.opengrabeso.loctio
package frontend
package views
package settings

import rest.RestAPI
import routing._
import io.udash._

import scala.concurrent.{ExecutionContext, Future}


/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState],
  rpc: RestAPI
)(implicit ec: ExecutionContext) extends Headers.PagePresenter[SettingsPageState.type](application) {

  val userSettings = ApplicationContext.serverSettings

  userSettings.subProp(_.timezone).streamTo(model.subProp(_.selectedTimezone))(identity)

  listAllTimezones()

  def handleState(state: SettingsPageState.type) = {}

  def submit(): Future[Unit] = {
    rpc.user(ApplicationContext.currentToken).settings(userSettings.get).map { _ =>
      userSettings.subProp(_.timezone).set(model.subProp(_.selectedTimezone).get)
    }
  }

  def guessTimezone(): Unit = {
    val userTimezone = TimeFormatting.timezone
    model.subProp(_.selectedTimezone).set(userTimezone)
  }

  def listAllTimezones() = {
    rpc.user(ApplicationContext.currentToken).listAllTimezones.foreach { zones =>
      model.subProp(_.timezones).set(zones)
    }
  }
}
