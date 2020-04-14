package com.github.opengrabeso.loctio.common.model

import io.udash.rest.RestDataCompanion

case class UserContext(userId: String, token: String)
object UserContext extends RestDataCompanion[UserContext]
