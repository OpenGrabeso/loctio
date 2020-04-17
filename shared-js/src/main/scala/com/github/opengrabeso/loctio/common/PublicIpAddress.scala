package com.github.opengrabeso.loctio.common

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

object PublicIpAddress {

  def get(implicit ec: ExecutionContext): Future[String] = {
    import com.softwaremill.sttp._

    // TODO: we should refresh public address from time to time, it can change (network change, physical computer location change)
    val request = sttp.get(uri"https://ipinfo.io/ip")

    val promise = Promise[String]()

    implicit val sttpBackend = io.udash.rest.DefaultSttpBackend()
    val response = request.send()
    response.onComplete {
      case Success(r) =>
        r.body match {
          case Right(string) =>
            println(s"Obtained a public IP address ${string.trim}")
            promise.success(string.trim)
          case Left(value) =>
            promise.failure(new UnsupportedOperationException(value))
        }
      case Failure(ex) =>
        promise.failure(ex)
    }
    promise.future
  }

}
