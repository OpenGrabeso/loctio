package com.github.opengrabeso.loctio
package frontend
package services

import scala.concurrent.{ExecutionContext, Future, Promise}
import UserContextService._
import io.udash.properties.model.ModelProperty

import scala.util.{Failure, Success}
import com.softwaremill.sttp._

object UserContextService {
  case class UserContextData(userId: String, token: String)
}

class UserContextService(val rpc: rest.RestAPI)(implicit ec: ExecutionContext) {

  val properties = ModelProperty(dataModel.SettingsModel())
  var userData: Promise[UserContextData] = _

  private val publicIpAddress = Promise[String]()

  private def requestPublicIpAddress(): Unit = {

    val request = sttp.get(uri"https://ipinfo.io/ip")

    implicit val backend = FetchBackend()
    val response = request.send()
    response.onComplete {
      case Success(r) =>
        r.body match {
          case Right(string) =>
            println(s"Obtained a public IP address ${string.trim}")
            publicIpAddress.success(string.trim)
          case Left(value) =>
            publicIpAddress.failure(new UnsupportedOperationException(value))
        }
      case Failure(ex) =>
        publicIpAddress.failure(ex)
    }
  }

  requestPublicIpAddress()

  println(s"Create UserContextService, token ${properties.subProp(_.token).get}")
  properties.subProp(_.token).listen {token =>
    println(s"listen: Start login $token")
    userData = Promise()
    val loginFor = userData // capture the value, in case another login starts for a different token before this one is completed
    rpc.user(token).name.onComplete {
      case Success(r) =>
        println(s"Login - new user $r")
        properties.subProp(_.login).set(r._1)
        properties.subProp(_.fullName).set(r._2)
        loginFor.success(UserContextData(r._1, token))

        for {
          context <- loginFor.future
          publicIp <- publicIpAddress.future
        } {
          rpc.user(context.token).report(publicIp, "online")
        }

      case Failure(ex) =>
        loginFor.failure(ex)
    }
  }

}
