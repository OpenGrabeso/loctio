package com.github.opengrabeso.loctio
package frontend
package views
package select

import common.UserState
import rest.RestAPI
import dataModel.SettingsModel
import common.model._
import routing._
import io.udash._
import io.udash.wrappers.jquery.jQ

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js.timers._
import scala.util.{Failure, Success, Try}

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState],
  rpc: RestAPI
)(implicit ec: ExecutionContext) extends Headers.PagePresenter[SelectPageState.type](application) {

  private val properties = ApplicationContext.settings

  import ApplicationContext.currentToken
  import ApplicationContext.currentLogin

  private def userAPI = rpc.user(ApplicationContext.currentToken)

  private var interval = Option.empty[SetIntervalHandle]
  private var lastActive: Long = System.currentTimeMillis()

  for {
    ip <- ApplicationContext.publicIpAddress
    _ <- ApplicationContext.userData.future
    _ <- ApplicationContext.serverSettingsLoading.future
  } {
    println("startListening")
    startListening(ip)
  }

  // must be called once both login and public IP address are known
  def startListening(ipAddress: String): Unit = {
    val token = ApplicationContext.currentToken
    assert(token.nonEmpty)
    assert(ipAddress.nonEmpty)

    lastActive = System.currentTimeMillis()

    //userAPI.shutdown(UserRestAPI.RestString("test"))

    refreshUsers(token, ipAddress)

    interval.foreach(clearInterval)
    interval = Some(setInterval(60000) { // once per minute

      // check if we are active or not
      // when not, do not report anything

      refreshUsers(token, ipAddress)
    })


    jQ("body").on("mousedown keydown touchstart mousemove scroll", (el, ev) => {
      lastActive = System.currentTimeMillis()
      //model.subProp(_.debug).set(s"Last active at $lastActive")
    })

    /* web page shutdown messes with the Tray utility, it is better not to report it
    // register the shutdown handler (beacon)
    val debugBeacon = false
    def onUnload() = dom.window.navigator.asInstanceOf[js.Dynamic].sendBeacon(s"/rest/user/$token/shutdown", "now")
    if (debugBeacon) {
      jQ(onUnload())
    } else {
      jQ(dom.window).on("unload", (_, _) => onUnload())
    }
    */
  }

  def loadUsersCallback(token: String, res: Try[Seq[(String, LocationInfo)]]) = {
    if (token == currentToken) { // ignore responses for a previous user (might be pending while the user is changed)
      val currentUser = currentLogin
      res match {
        case Success(value) =>
          model.subProp(_.users).set(UserState.userTable(currentUser, properties.subProp(_.state).get, value))
          model.subProp(_.loading).set(false)
        case Failure(exception) =>
          model.subProp(_.error).set(Some(exception))
          model.subProp(_.loading).set(false)
      }
    }
  }

  def refreshUsers(token: String, ipAddress: String): Unit = {
    val invisible = properties.subProp(_.state).get == "invisible"
    val sinceLastActiveMin = (System.currentTimeMillis() - lastActive) / 60000

    val state = if (invisible) "invisible" else if (sinceLastActiveMin < 5) "online" else "away"
    //println(s"List users $state")
    rpc.user(token).listUsers(ipAddress, state).onComplete(loadUsersCallback(token, _))
  }

  def refreshUsers(): Unit = {
    // should execute immediately, publicIpAddress should already be known at this point
    println("refreshUsers")
    ApplicationContext.publicIpAddress.foreach {
      refreshUsers(currentToken, _)
    }
  }

  def setLocationName(login: String, location: String): Unit = {
    userAPI.setLocationName(login, location).onComplete(loadUsersCallback(currentToken, _))
  }

  def watchUser(user: String): Unit = {
    // when we add a user, it is because we want to watch them
    userAPI.requestWatching(user).onComplete(loadUsersCallback(currentToken, _))
  }

  def addUser(user: String): Unit = {
    userAPI.addUser(user).onComplete(loadUsersCallback(currentToken, _))
  }

  def changeUserState(s: String): Unit = {
    properties.subProp(_.state).set(s)
    SettingsModel.store(properties.get)
    refreshUsers()
  }

  def requestWatching(login: String) = {
    userAPI.requestWatching(login).onComplete(loadUsersCallback(currentToken, _))
  }

  def stopWatching(login: String) = {
    userAPI.stopWatching(login).onComplete(loadUsersCallback(currentToken, _))
  }
  def allowWatchingMe(login: String) = {
    userAPI.allowWatchingMe(login).onComplete(loadUsersCallback(currentToken, _))
  }

  def disallowWatchingMe(login: String) = {
    userAPI.disallowWatchingMe(login).onComplete(loadUsersCallback(currentToken, _))
  }

  override def handleState(state: SelectPageState.type): Unit = {}
}
