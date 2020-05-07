package com.github.opengrabeso.loctio.rest.github

import io.udash.rest._

trait CommitsAPI {
  @Prefix("")
  def apply(ref: String): CommitsRefAPI
}

object CommitsAPI extends RestClientApiCompanion[EnhancedRestImplicits,CommitsAPI](EnhancedRestImplicits)
