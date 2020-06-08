package com.github.opengrabeso.loctio
package frontend
package views
package settings

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
)(implicit ec: ExecutionContext) extends Headers.PagePresenter[SettingsPageState.type](application) {
  private def properties: ModelProperty[SettingsModel] = model.subModel(_.settings)

  def init() = {
    // load the settings before installing the handler
    // otherwise both handlers are called, which makes things confusing
    val loaded = SettingsModel.load
    println(s"Loaded props $loaded")
    properties.set(loaded)
  }

  def handleState(state: SettingsPageState.type) = {}
}
