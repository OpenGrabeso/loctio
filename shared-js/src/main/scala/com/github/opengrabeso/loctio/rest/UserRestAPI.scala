package com.github.opengrabeso.loctio
package rest

import common.model._
import io.udash.rest._
import io.udash.rest.raw.HttpBody
import com.avsystem.commons.rpc.{AsRaw, AsReal}

import scala.concurrent.Future

trait UserRestAPI {
  import UserRestAPI._

  // login name, full name, role
  @GET
  def name: Future[(String, String, String)]

  @GET
  def settings: Future[UserSettings]

  @POST
  def settings(s: UserSettings): Future[Unit]

  @GET
  def listAllTimezones: Future[Seq[String]]

  @POST
  def listUsers(ipAddress: String, state: String): Future[Seq[(String, LocationInfo)]]

  @PUT
  def setLocationName(login: String, location: String): Future[Seq[(String, LocationInfo)]]

  /** @return user table HTML, tray icon status text */
  @GET
  def trayUsersHTML(ipAddress: String, state: String): Future[(String, String)]

  /**
   * @return application HTML, notification list, time to next poll in seconds */
  @GET
  def trayNotificationsHTML(): Future[(String, Seq[String], Int)]

  @PUT("users")
  def addUser(userName: String): Future[Seq[(String, LocationInfo)]]

  @POST
  @CustomBody
  def shutdown(data: RestString): Future[Unit]

  def requestWatching(user: String): Future[Seq[(String, LocationInfo)]]

  def stopWatching(user: String): Future[Seq[(String, LocationInfo)]]

  def allowWatchingMe(user: String): Future[Seq[(String, LocationInfo)]]

  def disallowWatchingMe(user: String): Future[Seq[(String, LocationInfo)]]
}

object UserRestAPI extends RestApiCompanion[EnhancedRestImplicits,UserRestAPI](EnhancedRestImplicits) {

  case class RestString(value: String) extends AnyVal
  object RestString extends RestDataWrapperCompanion[String, RestString] {
    implicit object asRaw extends AsRaw[HttpBody, RestString] {
      override def asRaw(real: RestString) = HttpBody.textual(real.value)
    }
    implicit object asReal extends AsReal[HttpBody, RestString] {
      override def asReal(raw: HttpBody) = RestString(raw.readText())
    }
  }
}