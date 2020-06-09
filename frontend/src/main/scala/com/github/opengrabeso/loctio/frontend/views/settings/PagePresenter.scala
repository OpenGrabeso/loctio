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

  def handleState(state: SettingsPageState.type) = {}

  def submit(): Future[Unit] = {
    rpc.user(ApplicationContext.currentToken).settings(ApplicationContext.serverSettings.get)
  }
}
