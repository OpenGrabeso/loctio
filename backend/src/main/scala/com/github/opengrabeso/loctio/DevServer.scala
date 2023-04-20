package com.github.opengrabeso.loctio

import com.github.opengrabeso.loctio.rest._
import com.github.opengrabeso.loctio.requests._
import io.udash.rest.raw.RawRest
import org.apache.commons.io.IOUtils

import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import io.udash.rest.RestServlet
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import monix.execution.Scheduler.Implicits.global

import java.lang.management.ManagementFactory

object DevServer {
  val portNumber = System.getenv.getOrDefault("PORT", "8080").toInt
  val GAE_APPLICATION = System.getenv.get("GAE_APPLICATION")
  val GAE_ENV = System.getenv.get("GAE_ENV")
  val GAE_RUNTIME = System.getenv.get("GAE_RUNTIME")


  val handlers: Seq[DefineRequest] = Seq(
    IndexHtml,

    FrontendStyle,
    FrontendScript, UdashApp, DebugTray, StaticContent,

    Cleanup
  )

  def main(args: Array[String]): Unit = {
    val runtimeMxBean = ManagementFactory.getRuntimeMXBean
    import scala.jdk.CollectionConverters._
    println(s"Java version ${System.getProperty("java.version")}, JVM arguments: ${runtimeMxBean.getInputArguments.asScala.mkString(" ")}")
    println(s"Starting Jetty at port $portNumber, environment $GAE_ENV")
    val server = new Server(portNumber)
    val handler = new ServletContextHandler
    handler.addServlet(new ServletHolder(new RestServlet(RawRest.asHandleRequest[RestAPI](RestAPIServer))), "/rest/*")

    server.setHandler(handler)

    for (h <- handlers) {
      import Method._
      // note: methods other than Get are currenly unused and therefore untested
      val s = h.method match {
        case Get =>
          new HttpServlet {
            override def doGet(req: HttpServletRequest, resp: HttpServletResponse) = h.handle(req, resp)
        }
        case Put =>
          new HttpServlet {
            override def doPut(req: HttpServletRequest, resp: HttpServletResponse) = h.handle(req, resp)
    }
        case Post =>
          new HttpServlet {
            override def doPost(req: HttpServletRequest, resp: HttpServletResponse) = h.handle(req, resp)
        }
        case Delete =>
          new HttpServlet {
            override def doDelete(req: HttpServletRequest, resp: HttpServletResponse) = h.handle(req, resp)
      }
    }

      handler.addServlet(new ServletHolder(s), h.handleUri)
  }

    server.start()
    server.join()
  }
}
