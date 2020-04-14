package com.github.opengrabeso.loctio
package frontend.views.select

import java.time.ZonedDateTime
import io.udash.HasModelPropertyCreator

case class UserRow(login: String, location: String, lastTime: Option[ZonedDateTime])

object UserRow extends HasModelPropertyCreator[UserRow]
