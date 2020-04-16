package com.github.opengrabeso.loctio
package frontend
package views
package select

import rest.RestAPI
import com.github.opengrabeso.loctio.dataModel.SettingsModel
import com.softwaremill.sttp.{dom => _, _}
import common.model._
import routing._
import io.udash._
import io.udash.wrappers.jquery.jQ
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Promise}
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

  def props: ModelProperty[SettingsModel] = model.subModel(_.settings)
  def properties = props

  private def currentToken: String = props.subProp(_.token).get
  private def currentLogin: String = props.subProp(_.login).get

  private val publicIp = Property[String]("")

  var userData: Promise[UserContextData] = _

  private val publicIpAddress = Promise[String]()

  private def requestPublicIpAddress(): Unit = {
    // TODO: we should refresh public address from time to time, it can change (network change, physical computer location change)
    val request = sttp.get(uri"https://ipinfo.io/ip")

    implicit val backend = FetchBackend()
    val response = request.send()
    response.onComplete {
      case Success(r) =>
        r.body match {
          case Right(string) =>
            println(s"Obtained a public IP address ${string.trim}")
            publicIpAddress.success(string.trim)
          case Left(value) =>
            publicIpAddress.failure(new UnsupportedOperationException(value))
        }
      case Failure(ex) =>
        publicIpAddress.failure(ex)
    }
  }

  requestPublicIpAddress()

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
        loginFor.success(UserContextData(r._1, token))

        for {
          context <- loginFor.future
          ip <- publicIpAddress.future
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
  }

  def init(): Unit = {
    // load the settings before installing the handler
    // otherwise both handlers are called, which makes things confusing
    val loaded = SettingsModel.load
    println(s"Loaded props $loaded")
    props.set(loaded)
  }


  def loadUsersCallback(token: String, res: Try[Seq[(String, LocationInfo)]]) = {
    if (token == currentToken) { // ignore responses for a previous user (might be pending while the user is changed)
      res match {
        case Success(value) =>
          model.subProp(_.loading).set(false)
          model.subProp(_.users).set(value.map { u =>
            if (u._1 == currentLogin) {
              val currentUserState = if (model.subProp(_.invisible).get) "invisible" else u._2.state
              UserRow(u._1, u._2.location, u._2.lastSeen, currentUserState)
            } else {
              UserRow(u._1, u._2.location, u._2.lastSeen, u._2.state)
            }
          })
          model.subProp(_.loading).set(false)
        case Failure(exception) =>
          model.subProp(_.error).set(Some(exception))
          model.subProp(_.loading).set(false)
      }
    }
  }

  def refreshUsers(token: String, ipAddress: String): Unit = {
    val invisible = model.subProp(_.invisible).get
    val sinceLastActiveMin = (System.currentTimeMillis() - lastActive) / 60000

    val state = if (invisible) "offline" else if (sinceLastActiveMin < 5) "online" else "away"
    rpc.user(token).listUsers(ipAddress, state).onComplete(loadUsersCallback(token, _))
  }

  def refreshUsers(): Unit = {
    refreshUsers(currentToken, publicIp.get)
  }

  def setLocationName(login: String, location: String): Unit = {
    val token = currentToken
    userAPI.setLocationName(login, location).onComplete(loadUsersCallback(token, _))
  }


  override def handleState(state: SelectPageState.type): Unit = {}
}
