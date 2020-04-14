package com.github.opengrabeso.mixtio

import java.awt.Desktop
import java.net.{URL, URLEncoder}
import java.util.concurrent.CountDownLatch

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.coding._
import akka.http.scaladsl.model.ContentType.WithCharset
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, BindFailedException}
import akka.util.ByteString

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise, duration}
import scala.util.{Failure, Success, Try}
import scala.xml.Elem
import java.time.{ZoneId, ZonedDateTime}

import com.github.opengrabeso.mixtio.rest.RestAPI
import com.softwaremill.sttp.SttpBackend
import common.Util._
import io.udash.rest.SttpRestClient
import io.udash.rest.raw.HttpErrorException
import shared.Digest

import scala.util.control.NonFatal

object Start extends App {

  case class AuthData(userId: String, since: ZonedDateTime, sessionId: String, authCode: String)

  private val instanceId = System.currentTimeMillis()
  private var authData = Option.empty[AuthData]
  private val authDone = new CountDownLatch(1)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  object HttpHandlerHelper {

    implicit class Prefixed(responseXml: Elem) {
      def prefixed: String = {
        val prefix = """<?xml version="1.0" encoding="UTF-8"?>""" + "\n"
        prefix + responseXml.toString
      }
    }

    private def sendResponseWithContentType(code: Int, response: String, ct: WithCharset) = {
      HttpResponse(status = code, entity = HttpEntity(ct, response))
    }

    def sendResponseHtml(code: Int, response: Elem): HttpResponse = {
      sendResponseWithContentType(code, response.toString, ContentTypes.`text/html(UTF-8)`)
    }

    def sendResponseXml(code: Int, responseXml: Elem): HttpResponse = {
      sendResponseWithContentType(code, responseXml.prefixed, ContentTypes.`text/xml(UTF-8)`)
    }
    def sendResponseBytes(code: Int, response: Array[Byte]): HttpResponse = {
      val ct = ContentTypes.`application/octet-stream`
      HttpResponse(status = code, entity = HttpEntity(ct, response))
    }
  }

  import HttpHandlerHelper._

  private val serverPort = 8088 // do not use 8080, would conflict with Google App Engine Dev Server

  private case class ServerInfo(system: ActorSystem, binding: Future[ServerBinding]) {
    def stop(): Future[Unit] = {
      implicit val executionContext = system.dispatcher
      // trigger unbinding from the port
      binding
        .flatMap(_.unbind())
        .flatMap(_ => system.terminate()) // and shutdown when done
        .map(_ => ())
    }

    def shutdown(): Future[Unit] = {
      implicit val executionContext = system.dispatcher
      binding
        .flatMap(_.unbind()) // trigger unbinding from the port
        .flatMap(_ => system.terminate()) // and shutdown when done
        .map(_ => ())

    }

  }

  private def shutdownAnotherInstance() = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val localServerUrl = s"http://localhost:$serverPort/shutdown?id=$instanceId"

    val localRequest = Http().singleRequest(HttpRequest(uri = localServerUrl)).map(_.discardEntityBytes())

    // try communicating with the local Stravimat, if not responding, use the remote one
    Try(Await.result(localRequest, Duration(2000, duration.MILLISECONDS)))
  }

  trait ServerUsed {
    def url: String
    def desciption: String
  }
  // GAE local server
  object ServerLocal8080 extends ServerUsed {
    def url = "http://localhost:8080"
    def desciption = " to local server"
  }
  // Jetty embedded server
  object ServerLocal4567 extends ServerUsed {
    def url = "http://localhost:4567"
    def desciption = " to local Jetty server"
  }
  // production server
  object ServerProduction extends ServerUsed {
    def url = "https://mixtio.appspot.com"
    override def desciption = ""
  }

  val server: ServerUsed = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val localTest = true // disabling the local test would make uploads faster for used of the production build (no need to wait for the probe timeout)
    val localFound = Promise[ServerUsed]()

    def localServerConfirmed(confirmed: ServerUsed): Unit = synchronized {
      println(s"Confirmed local server ${confirmed.url}")
      if (!localFound.tryComplete(Success(confirmed))) {
        // we always use only the first server confirmed
        // a developer should not run both
        println("Warning: it seems there are two local servers running")
      }
    }

    def tryLocalServer(s: ServerUsed) = {
      Http().singleRequest(HttpRequest(uri = s.url + "/ping")).map(_.discardEntityBytes()).map(_ => localServerConfirmed(s))
    }

    if (localTest) {
      tryLocalServer(ServerLocal8080)
      tryLocalServer(ServerLocal4567)
    }

    // try communicating with the local Stravimat, if not responding, use the remote one
    Try(Await.result(localFound.future, Duration(2000, duration.MILLISECONDS))).getOrElse(ServerProduction)
  }

  private val stravimatUrl = server.url

  private object Tray {
    import java.awt._
    import javax.swing._
    import java.awt.event._

    private var state: String = ""

    private def showImpl() = {
      assert(SwingUtilities.isEventDispatchThread)
      import javax.imageio.ImageIO
      // https://docs.oracle.com/javase/7/docs/api/java/awt/SystemTray.html

      if (SystemTray.isSupported) {
        try {
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
        } catch {
          case _: Exception =>
        }

        val tray = SystemTray.getSystemTray
        val iconSize = tray.getTrayIconSize
        val imageFile = if ((iconSize.height max iconSize.width) > 16) "/stravaUpload32.png" else "/stravaUpload16.png"
        val is = getClass.getResourceAsStream(imageFile)

        val image = ImageIO.read(is)
        val imageSized = image.getScaledInstance(iconSize.width, iconSize.height, Image.SCALE_SMOOTH)


        val trayIcon = new TrayIcon(imageSized, appName)

        import java.awt.event.MouseAdapter

        val popup = new JPopupMenu

        def addItem(title: String, action: => Unit) = {
          val listener = new ActionListener() {
            def actionPerformed(e: ActionEvent) = action
          }
          val item = new JMenuItem(title)
          item.addActionListener(listener)
          popup.add(item)
        }

        addItem("Login...", startBrowser())
        popup.addSeparator()
        addItem("Exit", doShutdown())


        trayIcon addMouseListener new MouseAdapter {
          override def mouseReleased(e: MouseEvent) = maybeShowPopup(e)

          override def mousePressed(e: MouseEvent) = maybeShowPopup(e)

          def maybeShowPopup(e: MouseEvent) = {
            if (e.isPopupTrigger) {
              popup.setLocation(e.getX, e.getY)
              popup.setInvoker(popup)
              popup.setVisible(true)
            }
          }
        }

        try {
          tray.add(trayIcon)
        } catch  {
          case e: AWTException =>
            e.printStackTrace()
        }
        Some(trayIcon)
      } else {
        None
      }
    }

    private def removeImpl(icon: TrayIcon): Unit = {
      assert(SwingUtilities.isEventDispatchThread)
      if (SystemTray.isSupported) {
        val tray = SystemTray.getSystemTray
        tray.remove(icon)
      }
    }

    private def changeStateImpl(icon: TrayIcon, s: String): Unit = {
      assert(SwingUtilities.isEventDispatchThread)
      state = s
      val title = appName
      val text = if (state.isEmpty) title else title + ": " + state
      icon.setToolTip(text)
    }

    private def loginDoneImpl(icon: TrayIcon): Unit = {
      icon.setPopupMenu(null)
    }


    def show(): Option[TrayIcon] = {
      val p = Promise[Option[TrayIcon]]
      SwingUtilities.invokeLater(new Runnable {
        override def run() = p.success(showImpl())
      })
      Await.result(p.future, Duration.Inf)
    }

    def remove(icon: TrayIcon): Unit = {
      SwingUtilities.invokeAndWait(new Runnable {
        override def run() = removeImpl(icon)
      })
    }

    def loginDone(icon: TrayIcon): Unit = {
      SwingUtilities.invokeAndWait(new Runnable {
        override def run() = loginDoneImpl(icon)
      })
    }

    def changeState(icon: TrayIcon, s: String): Unit = {
      SwingUtilities.invokeLater(new Runnable {
        override def run() = changeStateImpl(icon, s)
      })
    }
  }

  private def startBrowser() = {
    /**
    Authentication dance
    - request Stravimat to perform authentication, including user selection
     - http://stravimat/push-start?port=<XXXX>
    - Stravimat knowns or gets the Strava auth token (user id hash)
    - it generates a Stravimat token and sends it back by calling http://localhost:<XXXX>/auth?token=<ttttttttttt> (see [[startHttpServer]])
     - this is captured by [[com.github.opengrabeso.mixtio.Start.authHandler]] and redirected to /app#push
    */
    val sessionId = "push-session-" + System.currentTimeMillis().toString
    val startPushUrl = s"$stravimatUrl/push-start?port=$serverPort&session=$sessionId"
    println(s"Starting browser $startPushUrl")
    Desktop.getDesktop.browse(new URL(startPushUrl).toURI)
  }


  def authHandler(userId: String, since: String, sessionId: String, authCode: String) = {
    // session is authorized, we can continue sending the data
    serverInfo.stop()
    println(s"Auth done - $appName user id $userId, session $sessionId")
    val sinceTime = ZonedDateTime.parse(since)
    authData = Some(AuthData(userId, sinceTime, sessionId, authCode))
    authDone.countDown()
    val doPushUrl = s"$stravimatUrl/app#push/$sessionId"
    redirect(doPushUrl, StatusCodes.Found)
  }

  private def doShutdown(): Unit = {
    serverInfo.shutdown()
    authDone.countDown()
  }

  def shutdownHandler(id: Long): HttpResponse = {
    val response = if (id !=instanceId) {
      println(s"Shutdown - stop server $instanceId, received $id")
      doShutdown()
      <result>Done</result>
    } else {
      println(s"Shutdown ignored - same instance")
      <result>Ignored - same instance</result>
    }

    sendResponseXml(200, response)
  }

  private def startHttpServer(callbackPort: Int): ServerInfo = {

    import scala.concurrent.ExecutionContext.Implicits.global

    val requests = path("auth") {
      parameters('user, 'since, 'session) { (user, since, session) =>
        cookie("authCode") { authCode =>
          authHandler(user, since, session, authCode.value)
        }
      }
    } ~ path("shutdown") {
      parameter('id) {
        id =>
          complete(shutdownHandler(id.toLong))
      }
    }

    val route = get(requests)

    def startIt() = {
      Http().bindAndHandle(Route.handlerFlow(route), "localhost", callbackPort)
    }

    val bindingFuture: Future[ServerBinding] = startIt().recoverWith {
      case _: BindFailedException =>
        // an old hanging instance of this app may be already running, request it to shut down
        shutdownAnotherInstance()
        startIt()
    }

    println(s"Auth server $instanceId started, listening on http://localhost:$callbackPort")
    // TODO: we may never receive oauth answer, the session may be terminated or there may be an error
    // we should time-out gracefully in such case, as not to block the server port
    // currently we at least let the new server shut down us so that instances are not multiplying
    ServerInfo(system, bindingFuture)
  }

  private def waitForServerToStop(serverInfo: ServerInfo) = {
    try {
      val _ = Await.result(serverInfo.binding, Duration.Inf)
      authDone.await()
    } catch {
      case NonFatal(_) =>
        println("Server not started")
        serverInfo.system.terminate()
    }
  }

  def performUpload(data: AuthData) = {

    val AuthData(userId, since, sessionId, authCode) = data

    val sinceDate = since minusDays 1

    import scala.concurrent.ExecutionContext.Implicits.global

    val listFiles = MoveslinkFiles.listFiles.toList
    // sort files by timestamp
    val wantedFiles = listFiles.filter(MoveslinkFiles.timestampFromName(_).forall(_ > sinceDate))

    reportProgress(wantedFiles.size)

    val sortedFiles = wantedFiles.sortBy(MoveslinkFiles.timestampFromName)

    val localTimeZone = ZoneId.systemDefault.toString

    val useGzip = true
    // it seems production App Engine already decodes gziped request body, but development one does not
    // do not use encoding headers, as we want to encode / decoce on our own
    // this was done to keep payload small as a workaround for https://issuetracker.google.com/issues/63371955

    val api = {
      implicit val sttpBackend: SttpBackend[Future, Nothing] = SttpRestClient.defaultBackend()
      SttpRestClient[RestAPI](s"$stravimatUrl/rest")
    }

    val filesToSend = for {
      f <- sortedFiles
      fileBytes <- MoveslinkFiles.get(f)
    } yield {
      val digest = Digest.digest(fileBytes)
      (f, digest, fileBytes)
    }

    val createSession = api.uploadSession(userId, authCode, RestAPI.apiVersion)
    Try {
      Await.result(createSession, Duration.Inf)
    } match {
      case Success(pushSessionId) =>

        val userAPI = api.userAPI(userId, authCode, pushSessionId)

        val pushAPI = userAPI.push(sessionId, localTimeZone)

        val fileContent = filesToSend.map(f => f._1 -> (f._2, f._3)).toMap

        val toOffer = filesToSend.map(f => f._1 -> f._2)

        val wait = pushAPI.offerFiles(toOffer).map { needed =>
          reportProgress(needed.size)

          needed.foreach { id =>
            val (digest, content) = fileContent(id)
            def gzipEncoded(bytes: Array[Byte]) = if (useGzip) Gzip.encode(ByteString(bytes)) else ByteString(bytes)

            val upload = pushAPI.uploadFile(id, gzipEncoded(content).toArray, digest)
            // consider async processing here - a few requests in parallel could improve throughput
            Await.result(upload, Duration.Inf)
          }
        }
        Await.result(wait, Duration.Inf)
        reportProgress(0)
      case Failure(exception) =>
        println(s"Unable to connect to the server upload session, error $exception")
        // event if connecting to the session has failed, try to established a session to report the error
        // such session has no version requirements, therefore it should always succeed
        val reportError = api.reportUploadSessionError(userId, authCode)
        val pushSessionId = Await.result(reportError, Duration.Inf)


        val userAPI = api.userAPI(userId, authCode, pushSessionId)
        val errorString = exception match {
          case http: HttpErrorException =>
            if (http.payload.isEmpty) {
              s"HTTP Error ${http.code}"
            } else {
              s"HTTP Error ${http.code}: ${http.payload.get}"
            }
          case ex =>
            ex.toString
        }
        val wait = userAPI.push(sessionId, localTimeZone).reportError(errorString)
        Await.result(wait, Duration.Inf)
    }


  }

  val icon = Tray.show()

  def reportProgress(i: Int): Unit = {
    def state = s"Uploading $i files" + server.desciption
    icon.foreach(Tray.changeState(_, state))
  }

  private val serverInfo = startHttpServer(serverPort)

  startBrowser()

  waitForServerToStop(serverInfo)
  // server is stopped once auth information is received into authUserId, or when another instance has forced a shutdown

  icon.foreach(Tray.loginDone)

  for (data <- authData) {
    performUpload(data)
  }

  serverInfo.system.terminate()
  Await.result(serverInfo.system.whenTerminated, Duration.Inf)
  println("System stopped")
  icon.foreach(Tray.remove)
  // force stop - some threads seem to be preventing this and I am unable to find why
  System.exit(0)
}
