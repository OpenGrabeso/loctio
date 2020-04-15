package com.github.opengrabeso.loctio

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise, duration}
import scala.util.{Success, Try}


object Start extends App {

  case class AuthData(userId: String, since: ZonedDateTime, sessionId: String, authCode: String)

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  val exitEvent = Promise[Boolean]()

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
    def url = "https://loctio.appspot.com"
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
      Http().singleRequest(HttpRequest(uri = s.url + "/identity")).map(_.discardEntityBytes()).map(_ => localServerConfirmed(s))
    }

    if (localTest) {
      tryLocalServer(ServerLocal8080)
      tryLocalServer(ServerLocal4567)
    }

    // try communicating with the local Loctio, if not responding, use the remote one
    Try(Await.result(localFound.future, Duration(2000, duration.MILLISECONDS))).getOrElse(ServerProduction)
  }

  private val loctiotUrl = server.url

  def login() = {

  }

  def appExit() = {
    icon.foreach(Tray.remove)

    System.exit(0)
  }

  private object Tray {
    import java.awt._
    import java.awt.event._

    import javax.swing._

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

        addItem("Login...", login())
        popup.addSeparator()
        addItem("Exit", appExit())


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
      if (SwingUtilities.isEventDispatchThread) {
        removeImpl(icon)
      } else {
        SwingUtilities.invokeAndWait(new Runnable {
          override def run() = removeImpl(icon)
        })
      }
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

  val icon = Tray.show()

  def reportTray(message: String): Unit = {
    icon.foreach(Tray.changeState(_, message))
  }

  // wait until user ends the application

  Await.result(exitEvent.future, Duration.Inf)

  icon.foreach(Tray.remove)
  // force stop - some threads seem to be preventing this and I am unable to find why
  System.exit(0)
}
