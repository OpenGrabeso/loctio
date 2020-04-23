package com.github.opengrabeso.loctio
package requests

import org.apache.commons.io.IOUtils
import spark.{Request, Response}

object FrontendScript extends DefineRequest("frontend/*") {

  def html(request: Request, resp: Response) = {
    val scriptName = request.splat().head
    val moduleName = "frontend"
    val jsPath = scriptName match {
      case "script" =>
        Some("application/json", if (Main.devMode) s"/$moduleName-fastopt.js" else s"/$moduleName-opt.js")
      case "dependencies" =>
        Some("application/json", if (Main.devMode) s"/$moduleName-jsdeps.js" else s"/$moduleName-jsdeps.min.js")
      case _ =>
        None
    }
    jsPath.fold {
      resp.status(404)
    } { case (mime, jsPath) =>
      val res = getClass.getResourceAsStream(jsPath)

      resp.status(200)
      resp.`type`(mime)

      val out = resp.raw.getOutputStream
      IOUtils.copy(res, out)
      res.close()
      IOUtils.write("\n", out) // prevent empty file by always adding an empty line, empty file not handled well by Spark framework
    }

    Nil
  }
}
