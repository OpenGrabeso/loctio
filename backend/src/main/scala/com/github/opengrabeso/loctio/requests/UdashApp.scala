package com.github.opengrabeso.loctio
package requests

import spark.{Request, Response}

object UdashApp  extends DefineRequest("/app") {
  def html(request: Request, resp: Response) = {
    <html>
      <head>
        <meta charset="utf-8"/>
        <link rel="icon" href="static/favicon.ico"/>
        <title>{appName}</title>
        <script src="frontend/script"></script> {/* scala.js compilation result */}
        <script src="frontend/dependencies"></script>{/* scala.js dependencies */}
        <link href="frontend/main.css" rel="stylesheet" /> {/* Udash generated stylesheet*/ }
        <script>
          appMain()
        </script>
      </head>
      <body>
        <div id="application"></div>
      </body>
    </html>
  }

}
