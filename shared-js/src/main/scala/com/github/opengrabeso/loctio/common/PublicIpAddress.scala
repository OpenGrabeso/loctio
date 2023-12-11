package com.github.opengrabeso.loctio.common

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object PublicIpAddress {

  def get(implicit ec: ExecutionContext): Future[String] = {
    import sttp.client3._

    // TODO: we should refresh public address from time to time, it can change (network change, physical computer location change)
    val request = basicRequest.get(uri"https://ipinfo.io/ip")

    val promise = Promise[String]()

    val sttpBackend = io.udash.rest.DefaultSttpBackend()
    val response = request.send(sttpBackend)
    response.onComplete {
      case Success(r) =>
        r.body match {
          case Right(string) =>
            println(s"Obtained a public IP address ${string.trim}")
            promise.success(string.trim)
          case Left(value) =>
            println(s"Unable to obtain a public IP address: error ${r.code}")
            promise.success("")
        }
      case Failure(ex) =>
        // when failed, provide some dummy fallback
        println(s"Unable to obtain a public IP address, $ex")
        promise.success("")
    }
    promise.future
  }

}
