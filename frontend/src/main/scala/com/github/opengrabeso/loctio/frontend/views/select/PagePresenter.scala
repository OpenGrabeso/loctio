package com.github.opengrabeso.loctio
package frontend
package views
package select

import com.github.opengrabeso.loctio.dataModel.SettingsModel
import java.time.ZonedDateTime
import routing._
import io.udash._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

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
    props.listen { p =>
      loadUsers(p.token)
    }
    props.set(SettingsModel.load)
  }

  def loadUsers(token: String) = {
    model.subProp(_.loading).set(true)
    model.subProp(_.users).set(Nil)

    userService.rpc.user(token).listUsers.onComplete {
      case Success(value) =>
        model.subProp(_.users).set(value.map { u =>
          println(s"parse ${u._2.lastSeen}")
          UserRow(u._1, u._2.location, ZonedDateTime.parse(u._2.lastSeen))
        })
        model.subProp(_.loading).set(false)
      case Failure(exception) =>
        model.subProp(_.error).set(Some(exception))
        model.subProp(_.loading).set(false)
    }

  }

  override def handleState(state: SelectPageState.type): Unit = {}
}
