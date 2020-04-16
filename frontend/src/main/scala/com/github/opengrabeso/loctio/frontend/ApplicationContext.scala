package com.github.opengrabeso.loctio
package frontend

import rest.RestAPI
import routing._
import common.model._
import io.udash._

object ApplicationContext {
  private val routingRegistry = new RoutingRegistryDef
  private val viewFactoryRegistry = new StatesToViewFactoryDef

  val application = new Application[RoutingState](routingRegistry, viewFactoryRegistry)
  val rpc: RestAPI = com.github.opengrabeso.loctio.rest.RestAPIClient.api

  /*
  val userAPI = for {
    user <- facade.UdashApp.currentUserId
    authCode <- facade.UdashApp.currentAuthCode
  } yield {
    com.github.opengrabeso.loctio.rest.RestAPIClient.api.userAPI(user, authCode)
  }
  */

  application.onRoutingFailure {
    case _: SharedExceptions.UnauthorizedException =>
      // automatic redirection to AboutPage
      println("A routing failure: UnauthorizedException")
      application.goTo(SelectPageState)
  }
}