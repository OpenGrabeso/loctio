package com.github.opengrabeso.loctio
package frontend.routing

import io.udash._
import common.model._

import scala.scalajs.js.URIUtils

class RoutingRegistryDef extends RoutingRegistry[RoutingState] {
  def matchUrl(url: Url): RoutingState =
    url2State("/" + url.value.stripPrefix("/").stripSuffix("/"))

  def matchState(state: RoutingState): Url = Url(state2Url(state))

  object URIEncoded {
    def apply(s: String): String = URIUtils.encodeURIComponent(s)
    def unapply(s: String): Option[String] = Some(URIUtils.decodeURIComponent(s))
  }
  private val (url2State, state2Url) = bidirectional {
    case "/" => SelectPageState
  }
}