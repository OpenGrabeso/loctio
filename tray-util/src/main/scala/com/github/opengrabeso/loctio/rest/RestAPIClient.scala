package com.github.opengrabeso.loctio
package rest

import sttp.client3.SttpBackend
import io.udash.rest.SttpRestClient

import scala.concurrent.Future

object RestAPIClient {
  // single backend shared between all clients
  implicit val sttpBackend: SttpBackend[Future, Any] = SttpRestClient.defaultBackend()
  def fromUrl(url: String): RestAPI = {
    SttpRestClient[RestAPI, Future](url + "/rest")
  }
}
