package com.github.opengrabeso.mixtio
package requests

import spark.{Request, Response}

object UdashApp  extends DefineRequest("/app") {
  /*
  sessionId is a time when the initial session page was rendered on the server. A new page will constitute a new session.
  */
  def html(request: Request, resp: Response) = {
    <html>
      <head>
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
