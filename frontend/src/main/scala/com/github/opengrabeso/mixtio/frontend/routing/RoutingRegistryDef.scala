package com.github.opengrabeso.mixtio
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
  object ? {
    def apply(prefix: String, s: Seq[FileId]) = {
      prefix + s.map(f => URIEncoded(f.toString)).mkString("?", "&", "")
    }
    def unapply(s: String): Option[(String, Seq[FileId])] = {
      val prefix = s.takeWhile(_ != '?')
      if (prefix.nonEmpty) {
        val rest = s.drop(prefix.length + 1)
        val parts = rest.split("&")
        Some((prefix, parts.flatMap(URIEncoded.unapply(_).map(FileId.parse))))
      } else {
        None
      }
    }
  }
  private val (url2State, state2Url) = bidirectional {
    case "/" => SelectPageState
    case "/settings" => SettingsPageState
    case "/push" / session => PushPageState(session)
    case "/edit" ? s => EditPageState(s)
  }
}