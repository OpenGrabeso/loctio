package com.github.opengrabeso.loctio
package rest

import com.softwaremill.sttp.SttpBackend
import io.udash.rest.SttpRestClient

import scala.concurrent.Future

object RestAPIClient {
  // single backend shared between all clients
  implicit val sttpBackend: SttpBackend[Future, Nothing] = SttpRestClient.defaultBackend()
  def fromUrl(url: String): RestAPI = {
    SttpRestClient[RestAPI](url + "/rest")
  }
}
