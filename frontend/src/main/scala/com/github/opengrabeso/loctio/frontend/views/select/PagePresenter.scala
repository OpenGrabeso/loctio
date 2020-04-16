package com.github.opengrabeso.loctio
package frontend
package views
package select

import com.github.opengrabeso.loctio.dataModel.SettingsModel
import java.time.ZonedDateTime

import common.model._
import routing._
import io.udash._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js.timers._
import scala.util.{Failure, Success, Try}

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState],
  userService: services.UserContextService
)(implicit ec: ExecutionContext) extends Headers.PagePresenter[SelectPageState.type](application) {

  def props: ModelProperty[SettingsModel] = userService.properties
  private def currentToken: String = props.subProp(_.token).get
  private def userAPI = userService.rpc.user(currentToken)

  var interval = Option.empty[SetIntervalHandle]

  def init(): Unit = {
    // load the settings before installing the handler
    // otherwise both handlers are called, which makes things confusing
    println("Loading props")
    props.listen { p =>
      loadUsers(p.token)
      interval.foreach(clearInterval)
      interval = Some(setInterval(60000) { // once per minute

        // check if we are active or not
        // when not, do not report anything

        refreshUsers()
      })
    }
    val loaded = SettingsModel.load
    println(s"Loaded props $loaded")
    props.set(loaded)
  }


  def loadUsersCallback(res: Try[Seq[(String, LocationInfo)]]) = res match {
    case Success(value) =>
      model.subProp(_.users).set(value.map { u =>
        UserRow(u._1, u._2.location, u._2.lastSeen, u._2.state)
      })
      model.subProp(_.loading).set(false)
    case Failure(exception) =>
      model.subProp(_.error).set(Some(exception))
      model.subProp(_.loading).set(false)
  }

  private def doLoadUsers(token: String) = {
    userService.rpc.user(token).listUsers.onComplete(loadUsersCallback)
  }
  def loadUsers(token: String) = {
    model.subProp(_.loading).set(true)
    model.subProp(_.users).set(Nil)
    doLoadUsers(token)
  }

  def refreshUsers() = {
    doLoadUsers(currentToken)
  }

  def setLocationName(login: String, location: String): Unit = {
    userAPI.setLocationName(login, location).onComplete(loadUsersCallback)
  }


  override def handleState(state: SelectPageState.type): Unit = {}
}
