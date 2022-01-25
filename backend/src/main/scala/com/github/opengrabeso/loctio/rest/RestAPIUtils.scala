package com.github.opengrabeso.loctio.rest

import scala.concurrent.Future

trait RestAPIUtils {
  def syncResponse[T](t: =>T): Future[T] = {
    Future.successful(t)
  }
}
