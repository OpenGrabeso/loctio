package com.github.opengrabeso.mixtio
package frontend
package views
package push

import routing._
import io.udash._

import scala.concurrent.ExecutionContext

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  userContextService: services.UserContextService,
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[PushPageState] with settings_base.SettingsPresenter {

  // time changes once per 1000 ms, but we do not know when. If one would use 1000 ms, the error could be almost 1 sec if unlucky.
  // By using 200 ms we are sure the error will be under 200 ms
  init(model.subModel(_.s), userContextService)

  /** We don't need any initialization, so it's empty. */
  override def handleState(state: PushPageState): Unit = {
  }

  def gotoSelect(): Unit = {
    application.goTo(SelectPageState)
  }
}
