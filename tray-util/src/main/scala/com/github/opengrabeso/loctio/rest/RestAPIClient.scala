package com.github.opengrabeso.loctio
package rest

import com.softwaremill.sttp.SttpBackend
import io.udash.rest.SttpRestClient

import scala.concurrent.Future

object RestAPIClient {
  def fromUrl(url: String): RestAPI = {
    implicit val sttpBackend: SttpBackend[Future, Nothing] = SttpRestClient.defaultBackend()
    SttpRestClient[RestAPI](url + "/rest")
  }
}
