package com.github.opengrabeso.loctio
package frontend

import common.PublicIpAddress
import rest.RestAPI
import routing._
import common.model._
import io.udash.{properties, _}

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}

object ApplicationContext {
  case class UserContextData(userId: String, token: String)

  implicit val ec = ExecutionContext.global

  private val routingRegistry = new RoutingRegistryDef
  private val viewFactoryRegistry = new StatesToViewFactoryDef

  val application = new Application[RoutingState](routingRegistry, viewFactoryRegistry)
  val rpc: RestAPI = com.github.opengrabeso.loctio.rest.RestAPIClient.api

  val settings = ModelProperty(dataModel.SettingsModel.load) // local settings
  val serverSettings = ModelProperty(UserSettings())

  val publicIpAddress = PublicIpAddress.get

  def currentToken: String = settings.subProp(_.token).get
  def currentLogin: String = settings.subProp(_.login).get

  var userData: Promise[UserContextData] = Promise.failed(new NoSuchElementException)
  var serverSettingsLoading: Promise[UserSettings] = Promise.failed(new NoSuchElementException) // server based settings

  println(s"Create UserContextService, token ${settings.subProp(_.token).get}")
  settings.subProp(_.token).listen {token =>
    println(s"listen: Start login $token")
    userData = Promise()
    serverSettingsLoading = Promise()
    val loginFor = userData // capture the value, in case another login starts for a different token before this one is completed
    val settingsFor = serverSettingsLoading
    rpc.user(token).settings.onComplete {
      case Success(s) =>
        settingsFor.success(s)
        serverSettings.set(s)
      case Failure(ex) =>
        val default = UserSettings(timezone = views.TimeFormatting.timezone)
        settingsFor.success(default)
        serverSettings.set(default)
    }
    rpc.user(token).name.onComplete {
      case Success(r) =>
        println(s"Login - new user $r")
        settings.subProp(_.login).set(r._1)
        settings.subProp(_.fullName).set(r._2)
        settings.subProp(_.role).set(r._3)
        loginFor.success(UserContextData(r._1, token))

      case Failure(ex) =>
        loginFor.failure(ex)
    }
  }
  settings.touch()

  application.onRoutingFailure {
    case _: SharedExceptions.UnauthorizedException =>
      // automatic redirection to AboutPage
      println("A routing failure: UnauthorizedException")
      application.goTo(SelectPageState)
  }
}