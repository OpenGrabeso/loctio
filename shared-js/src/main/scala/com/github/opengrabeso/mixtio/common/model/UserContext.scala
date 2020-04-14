package com.github.opengrabeso.mixtio.common.model

import io.udash.rest.RestDataCompanion

case class UserContext(userId: String, authCode: String)
object UserContext extends RestDataCompanion[UserContext]
