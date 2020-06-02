package com.github.opengrabeso.loctio
package frontend
package views
package select

import com.github.opengrabeso.loctio.common.{PublicIpAddress, UserState}
import rest.RestAPI
import dataModel.SettingsModel
import common.model._
import routing._
import io.udash._
import io.udash.wrappers.jquery.jQ
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Promise}
import scala.scalajs.js
import scala.scalajs.js.timers._
import scala.util.{Failure, Success, Try}


object PagePresenter {
  case class UserContextData(userId: String, token: String)
}

import PagePresenter._

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  application: Application[RoutingState],
  rpc: RestAPI
)(implicit ec: ExecutionContext) extends Headers.PagePresenter[SelectPageState.type](application) {

  private def properties: ModelProperty[SettingsModel] = model.subModel(_.settings)

  private def currentToken: String = properties.subProp(_.token).get
  private def currentLogin: String = properties.subProp(_.login).get

  private val publicIp = Property[String]("")

  var userData: Promise[UserContextData] = _

  private val publicIpAddress = PublicIpAddress.get(ec)

  println(s"Create UserContextService, token ${properties.subProp(_.token).get}")
  properties.subProp(_.token).listen {token =>
    println(s"listen: Start login $token")
    userData = Promise()
    val loginFor = userData // capture the value, in case another login starts for a different token before this one is completed
    rpc.user(token).name.onComplete {
      case Success(r) =>
        println(s"Login - new user $r")
        properties.subProp(_.login).set(r._1)
        properties.subProp(_.fullName).set(r._2)
        properties.subProp(_.role).set(r._3)
        loginFor.success(UserContextData(r._1, token))

        for {
          context <- loginFor.future // we need to wait for this before calling startListening
          ip <- publicIpAddress
        } {
          publicIp.set(ip)
          startListening()
        }

      case Failure(ex) =>
        loginFor.failure(ex)
    }
  }




  private def userAPI = rpc.user(currentToken)

  private var interval = Option.empty[SetIntervalHandle]
  private var lastActive: Long = System.currentTimeMillis()

  // must be called once both login and public IP address are known
  def startListening(): Unit = {
    val token = currentToken
    val ipAddress = publicIp.get
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

  def init(): Unit = {
    // load the settings before installing the handler
    // otherwise both handlers are called, which makes things confusing
    val loaded = SettingsModel.load
    println(s"Loaded props $loaded")
    properties.set(loaded)
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
    refreshUsers(currentToken, publicIp.get)
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
