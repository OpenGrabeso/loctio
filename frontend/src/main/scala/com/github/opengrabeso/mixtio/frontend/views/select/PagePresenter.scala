package com.github.opengrabeso.mixtio
package frontend
package views
package select

import routing._
import io.udash._

import scala.concurrent.{ExecutionContext, Future, Promise}

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState],
  userService: services.UserContextService
)(implicit ec: ExecutionContext) extends Presenter[SelectPageState.type] {

  def loadUsers() = {
    model.subProp(_.loading).set(true)
    model.subProp(_.users).set(Nil)
    // TODO: real loading
  }

  override def handleState(state: SelectPageState.type): Unit = {}
}
