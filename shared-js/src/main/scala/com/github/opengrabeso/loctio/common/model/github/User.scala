package com.github.opengrabeso.loctio
package common.model.github

import rest.EnhancedRestDataCompanion

case class User( // https://developer.github.com/v3/users/#get-the-authenticated-user
  login: String,
  id: Long,
  url: String = "???",
  name: String = null
) {
  def displayName: String = if (name != null) name else login
}

object User extends EnhancedRestDataCompanion[User]
