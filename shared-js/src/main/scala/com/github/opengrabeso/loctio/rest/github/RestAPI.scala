package com.github.opengrabeso.loctio
package rest.github

import io.udash.rest._

trait RestAPI {
  @Prefix("")
  def authorized(@Header("Authorization") bearer: String): AuthorizedAPI
}

object RestAPI extends RestClientApiCompanion[EnhancedRestImplicits,RestAPI](EnhancedRestImplicits)
