package com.github.opengrabeso.loctio
package requests

import spark.{Request, Response}

object DebugTray extends DefineRequest("/debug-tray") {
  def html(request: Request, resp: Response) = {
    xml.Unparsed(
      s"""
      <html>
        <head>
          <meta charset="utf-8"/>
          <link rel="icon" href="static/favicon.ico"/>
          <title>${appName} - Tray HTML Debugging</title>
        </head>
        <body>
        </body>
      </html>
      """
    )
  }

}
