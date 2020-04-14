package com.github.opengrabeso.loctio
package frontend
package services

import scala.concurrent.{ExecutionContext, Promise}
import UserContextService._
import io.udash.properties.model.ModelProperty

import scala.util.{Failure, Success}

object UserContextService {
  class UserContextData(userId: String, token: String)
}

class UserContextService(rpc: rest.RestAPI)(implicit ec: ExecutionContext) {

  val properties = ModelProperty(dataModel.SettingsModel())
  var userData: Promise[UserContextData] = _

  println(s"Create UserContextService, token ${properties.subProp(_.token).get}")
  properties.subProp(_.token).listen {token =>
    println(s"listen: Start login $token")
    userData = Promise()
    val loginFor = userData // capture the value, in case another login starts for a different token before this one is completed
    rpc.user(token).name.onComplete {
      case Success(r) =>
        println(s"Login - new user $r")
        properties.subProp(_.login).set(r)
        properties.subProp(_.fullName).set(r)
        loginFor.success(new UserContextData(r, token))
      case Failure(ex) =>
        loginFor.failure(ex)
    }
  }
}
