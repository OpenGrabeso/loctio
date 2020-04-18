package com.github.opengrabeso.loctio
package common
package model

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion
import io.udash.properties.ModelPropertyCreator

case class UserRow(login: String, location: String, lastTime: ZonedDateTime, lastState: String)

object UserRow extends EnhancedRestDataCompanion[UserRow] {
  implicit val modelPropertyCreator = ModelPropertyCreator.materialize[UserRow]
}
