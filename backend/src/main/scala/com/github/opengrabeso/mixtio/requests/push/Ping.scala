package com.github.opengrabeso.mixtio
package requests
package push

import spark.{Request, Response}

// used by push-uploader to check if a local server is running
object Ping extends DefineRequest("/ping") {
  def html(req: Request, resp: Response) = {
    <ok>OK</ok>
  }
}
