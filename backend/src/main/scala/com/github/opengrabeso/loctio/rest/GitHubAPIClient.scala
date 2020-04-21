package com.github.opengrabeso.loctio
package rest


import com.avsystem.commons.misc.Opt
import com.avsystem.commons.rpc.AsReal
import com.avsystem.commons.serialization.GenCodec
import common.model.github._
import rest.github._
import com.softwaremill.sttp._
import io.udash.rest.raw._
import io.udash.rest.{RestException, SttpRestClient}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import shared.ChainingSyntax._

class GitHubAPIClient(sttpBackend: SttpBackend[Id, Nothing]) { // TODO: DRY with GitHubAPIClient in trayUtil
  private implicit val backend = sttpBackend
  val api: github.RestAPI = CustomRestClient[github.RestAPI]("https://api.github.com")

  // adapted from io.udash.rest.SttpRestClient#fromSttpResponse
  // we cannot use it directly, as it is private there
  private def fromSttpResponse(sttpResp: Response[String]): RestResponse = RestResponse(
    sttpResp.code,
    IMapping(sttpResp.headers.iterator.map { case (n, v) => (n, PlainValue(v)) }.toList),
    sttpResp.contentType.fold(HttpBody.empty) { contentType =>
      val mediaType = HttpBody.mediaTypeOf(contentType)
      HttpBody.charsetOf(contentType) match {
        case Opt(charset) =>
          val text = sttpResp.body.fold(identity, identity)
          HttpBody.textual(text, mediaType, charset)
        case _ =>
          HttpBody.textual(sttpResp.unsafeBody, contentType)
      }
    }
  )

  // many APIs return URLs which should be used to obtain more information - this can be used to have the result decoded
  def request[T](uri: String, token: String, method: Method = Method.GET)(implicit asReal: AsReal[RestResponse, T]): T = {
    val request = sttp.method(method, uri"$uri").auth.bearer(token)
    sttpBackend.send(request).pipe { r =>
      val raw = fromSttpResponse(r)
      implicitly[AsReal[RestResponse, T]].asReal(raw)
    }
  }

  // used for issue paging - use the provided URL and process the result headers
  def requestWithHeaders[T: GenCodec](uri: String, token: String): DataWithHeaders[Seq[T]] = {
    val request = sttp.method(Method.GET, uri"$uri").auth.bearer(token)

    sttpBackend.send(request).pipe { r =>
      val raw = fromSttpResponse(r)
      import io.udash.rest.GenCodecRestImplicits._
      github.EnhancedRestImplicits.fromResponse[T].asReal(raw)
    }
  }
}
