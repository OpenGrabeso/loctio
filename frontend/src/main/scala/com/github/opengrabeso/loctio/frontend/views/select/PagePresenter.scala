package com.github.opengrabeso.loctio
package frontend
package views
package select

import com.github.opengrabeso.loctio.dataModel.SettingsModel
import routing._
import io.udash._

import scala.concurrent.{ExecutionContext, Future, Promise}

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState],
  userService: services.UserContextService
)(implicit ec: ExecutionContext) extends Presenter[SelectPageState.type] {

  def props: ModelProperty[SettingsModel] = userService.properties

  def init(): Unit = {
    // load the settings before installing the handler
    // otherwise both handlers are called, which makes things confusing
    props.set(SettingsModel.load)
  }

  def loadUsers() = {
    model.subProp(_.loading).set(true)
    model.subProp(_.users).set(Nil)
    // TODO: real loading
  }

  override def handleState(state: SelectPageState.type): Unit = {}
}
