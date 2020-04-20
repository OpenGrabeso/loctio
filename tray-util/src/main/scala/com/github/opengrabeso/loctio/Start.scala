package com.github.opengrabeso.loctio

import java.awt.Desktop
import java.net.URL
import java.time.{ZoneId, ZonedDateTime}
import java.time.format._
import java.time.temporal.ChronoUnit
import java.util.Locale

import akka.actor.{ActorSystem, Cancellable}
import com.github.opengrabeso.loctio.common.PublicIpAddress
import com.github.opengrabeso.loctio.common.model.{LocationInfo, UserRow}
import javax.swing.SwingUtilities
import rest.{RestAPI, RestAPIClient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise, duration}
import scala.swing._
import scala.util.{Failure, Success}
import scala.swing.Swing._
import shared.ChainingSyntax._

import scala.concurrent.ExecutionContext.global
import shared.FutureAt._

object Start extends SimpleSwingApplication {

  implicit val system = ActorSystem()

  val exitEvent = Promise[Boolean]()

  private var cfg = Config.load
  private var loginName = ""
  private var usersReady = false
  private var updateSchedule: Cancellable = _

  trait ServerUsed {
    def url: String
    def description: String
    override def toString = url

    val api: RestAPI = RestAPIClient.fromUrl(url)
  }
  // GAE local server
  object ServerLocal8080 extends ServerUsed {
    def url = "http://localhost:8080"
    def description = " to local server"
  }
  // Jetty embedded server
  object ServerLocal4567 extends ServerUsed {
    def url = "http://localhost:4567"
    def description = " to local Jetty server"
  }
  // production server
  object ServerProduction extends ServerUsed {
    def url = "https://loctio.appspot.com"
    override def description = ""
  }

  val server: Future[ServerUsed] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val localTest = true // disabling the local test would make uploads faster for used of the production build (no need to wait for the probe timeout)
    val serverFound = Promise[ServerUsed]()

    def localServerConfirmed(confirmed: ServerUsed): Unit = synchronized {
      println(s"Confirmed local server ${confirmed.url}")
      if (!serverFound.tryComplete(Success(confirmed))) {
        // we always use only the first server confirmed
        // a developer should not run both
        println(s"Warning: we are already connected to ${serverFound.future.value.flatMap(_.toOption).getOrElse("None")}")
      }
    }

    def tryLocalServer(s: ServerUsed) = {
      s.api.identity("ping").foreach(_ => localServerConfirmed(s))
    }

    if (localTest) {
      tryLocalServer(ServerLocal4567)
      tryLocalServer(ServerLocal8080)
    }

    system.scheduler.scheduleOnce(Duration(2000, duration.MILLISECONDS)) {
      serverFound.trySuccess(ServerProduction)
    }

    serverFound.future
  }

  def userApi: Future[rest.UserRestAPI] = if (cfg.token.nonEmpty) {
    server.at(global).map(_.api.user(cfg.token))
  } else {
    Future.failed(throw new NoSuchElementException("No token provided"))
  }

  def login(location: Point) = {
    assert(SwingUtilities.isEventDispatchThread)
    val frame = loginFrame
    placeFrameAbove(frame, location)
    frame.init()
    frame.open()
  }

  def performLogin(token: String): Unit = {
    usersReady = false
    loginName = ""
    mainFrame.setUsers(Seq.empty)
    server.at(global).flatMap(_.api.user(token).name).at(global).map(_._1).at(OnSwing).foreach { s =>
      loginName = s
      println(s"Login done $s")
      // request users regularly
      if (updateSchedule != null) updateSchedule.cancel()
      updateSchedule = system.scheduler.schedule(Duration(0, duration.MINUTES), Duration(1, duration.MINUTES)){
        requestUsers.at(OnSwing).foreach { users =>
          if (token == cfg.token) { // ignore any pending futures with a different token
            usersReady = true
            mainFrame.setUsers(users)
            println("User list ready")
          }
        }
      }(global)
    }
  }

  // make sure frame is on screen
  def placeFrame(frame: Frame, location: Point) = {
    val config = frame.peer.getGraphicsConfiguration
    val bounds = config.getBounds
    val insets = java.awt.Toolkit.getDefaultToolkit.getScreenInsets(config)

    val minX = bounds.x + insets.left
    val minY = bounds.y + insets.top
    val maxX = bounds.x + bounds.width - insets.right - frame.size.width
    val maxY = bounds.y + bounds.height - insets.bottom - frame.size.height

    val loc = new Point(minX max location.x min maxX, minY max location.y min maxY)
    frame.location = loc
  }

  def placeFrameAbove(frame: Frame, location: Point) = {
    placeFrame(frame, new Point(location.x, location.y - frame.size.height - 150))
  }

  def openWindow(location: Point): Unit = {
    // place the window a bit above the mouse - this avoid conflicting with the menu
    placeFrameAbove(mainFrame, location)
    if (usersReady) {
      mainFrame.open()
    }
  }

  def openWeb(location: Point): Unit = {
    Desktop.getDesktop.browse(new URL(s"https://${appName.toLowerCase}.appspot.com").toURI)
  }

  def appExit() = {
    println("appExit")
    icon.foreach(Tray.remove)
    userApi.at(global).flatMap(_.shutdown(rest.UserRestAPI.RestString("now"))).at(OnSwing).foreach {_ =>
      System.exit(0)
    }
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
        val is = getClass.getResourceAsStream("/user-online.ico")

        val image = ImageIO.read(is)

        val imageSized = image.getScaledInstance(iconSize.width, iconSize.height, Image.SCALE_SMOOTH)
        val trayIcon = new TrayIcon(imageSized, appName)

        import java.awt.event.MouseAdapter

        val popup = new JPopupMenu

        def addItem(title: String, action: Point => Unit): JMenuItem = {
          val item = new JMenuItem(title)
          object listener extends ActionListener {
            def actionPerformed(e: ActionEvent) = {
              action(MouseInfo.getPointerInfo.getLocation)
            }
          }
          item.addActionListener(listener)
          popup.add(item)
          item
        }

        val openItem = addItem("Open...", openWindow)
        addItem("Open web...", openWeb)
        popup.addSeparator()
        addItem("Login...", login)
        popup.addSeparator()
        addItem("Exit", _ => appExit())

        def showPopup(e: MouseEvent) = {
          openItem.setEnabled(usersReady)
          popup.setLocation(e.getX, e.getY)
          popup.setInvoker(popup)
          popup.setVisible(true)
        }

        trayIcon addMouseListener new MouseAdapter {

          override def mouseClicked(e: MouseEvent) = if (e.getButton == 1) openWindow(new Point(e.getPoint))

          override def mouseReleased(e: MouseEvent) = maybeShowPopup(e)

          override def mousePressed(e: MouseEvent) = maybeShowPopup(e)

          private def maybeShowPopup(e: MouseEvent) = {
            if (e.isPopupTrigger) {
              showPopup(e)
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
      val text = if (state.isEmpty) title else state
      icon.setToolTip(text)
    }

    def swingInvokeAndWait[T](callback: => T): T = {
      if (SwingUtilities.isEventDispatchThread) {
        callback
      } else {
        val p = Promise[T]
        OnSwing.future(p.success(callback))
        Await.result(p.future, Duration.Inf)
      }
    }
    def show(): Option[TrayIcon] = {
      swingInvokeAndWait(showImpl())
    }

    def remove(icon: TrayIcon): Unit = {
      // wait to be sure the thread removing the icon is not terminated by exit before the removal is completed
      swingInvokeAndWait(removeImpl(icon))
    }

    def changeState(icon: TrayIcon, s: String): Unit = {
      OnSwing.future {
        changeStateImpl(icon, s)
      }
    }
  }

  val icon = Tray.show()

  def reportTray(message: String): Unit = {
    icon.foreach(Tray.changeState(_, message))
  }

  object mainFrame extends Frame {
    assert(SwingUtilities.isEventDispatchThread)

    title = appName

    val panel = new Label()
    panel.font = panel.font.deriveFont(panel.font.getSize2D * 1.2f)

    val columns = Seq("", "User", "Location", "Last seen")
    contents = new ScrollPane(
      new BoxPanel(Orientation.Vertical) {
        contents += panel
      }
    )

    def setUsers(us: Seq[(String, LocationInfo)]): this.type = {
      val table = common.UserState.userTable(loginName, false, us)

      val loc = Locale.getDefault(Locale.Category.FORMAT)
      val fmtTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(loc)
      val fmtDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(loc)
      val fmtDayOfWeek = DateTimeFormatter.ofPattern("eeee", loc) // Exactly 4 pattern letters will use the full form
      val zone = ZoneId.systemDefault()

      def displayTime(t: ZonedDateTime) = {
        common.UserState.smartTime(t.withZoneSameInstant(zone), fmtTime.format, fmtDate.format, fmtDayOfWeek.format)
      }

      def userStateDisplay(state: String) = {
        state match { // from https://www.alt-codes.net/circle-symbols
          case "online" => ("green", "⚫")
          case "offline" => ("gray", "⦾")
          case "away" => ("yellow", "⦿")
          case "busy" => ("red", "⚫")
        }
      }

      def userStateHtml(state: String) = {
        // consider using inline images (icons) instead
        val (color, text) = userStateDisplay(state)
        s"<span style='color: $color'>$text</span>"
      }

      def userRowHTML(row: common.model.UserRow) = {
        //language=HTML
        s"""<tr>
           <td>${userStateHtml(row.currentState)}</td>
           <td>${row.login}</td>
           <td>${row.location}</td>
           <td>${if (row.currentState != "online") displayTime(row.lastTime) else ""}</td>
           </tr>
          """
      }


      val tableHTML = //language=HTML
        s"""<html>
            <head>
            <style>
            table {
              border-collapse: collapse;
            }
            table {
              border: 1px none #ff0000;
            }
            th, td {
              border: 1px solid #e0e0e0;
            }
            </style>
            </head>
            <body>
              <table>
              <tr>${columns.map(c => s"<th>$c</th>").mkString}</tr>
              ${table.map(userRowHTML).mkString}
              </table>
            </body>
           </html>
        """
      panel.text = tableHTML
      pack()
      def trayUserLine(u: UserRow) = {
        val stateText = userStateDisplay(u.currentState)._2
        if (u.currentState != "offline") {
          s"$stateText ${u.login}: ${u.location}"
        } else {
          s"$stateText ${u.login}: ${displayTime(u.lastTime)}"
        }
      }
      val onlineUsers = table.filter(u => u.login != loginName && u.lastTime.until(ZonedDateTime.now(), ChronoUnit.DAYS) < 7)
      val statusText = onlineUsers.map(u => trayUserLine(u)).mkString("\n")
      reportTray(statusText)
      this
    }
  }


  override def top = mainFrame

  object loginFrame extends Frame { dialog =>
    assert(SwingUtilities.isEventDispatchThread)
    title = appName
    private val tokenField = new TextField("")
    private val loginField = new Label("")

    def init(): Unit = {
      assert(SwingUtilities.isEventDispatchThread)
      tokenField.text = ""
      // workaround hack: setting loginField.text directly does not work - no idea why
      // the text is changed, but the position and the extents (bounding box) is not
      OnSwing.future {
        loginField.text = s"Currently logged in as $loginName"
        pack()
      }
    }

    contents = new BoxPanel(Orientation.Vertical) {
      contents += new BoxPanel(Orientation.Horizontal) {
        contents += loginField
      }
      contents += new BoxPanel(Orientation.Horizontal) {
        contents += new Label("Enter your GitHub token (no scopes necessary):")
      }
      contents += new BoxPanel(Orientation.Horizontal) {
        contents += tokenField.tap(_.columns = 40)
      }
      contents += new BoxPanel(Orientation.Horizontal) {
        contents += HGlue
        contents += new Button("OK") {
          reactions += {
            case event.ButtonClicked(_) =>
              if (tokenField.text.nonEmpty) {
                cfg = cfg.copy(token = tokenField.text)
                Config.store(cfg)
                performLogin(cfg.token)
              }
              dialog.close()
          }
        }
        contents += new Button("Cancel") {
          reactions += {
            case event.ButtonClicked(_) =>
              dialog.close()
          }
        }
      }
    }
  }

  // override, we do not want to show the window when started
  override def startup(args: Array[String]): Unit = {
    val t = top
    if (t.size == new Dimension(0,0)) t.pack()
    //t.open()
  }

  if (cfg.token.nonEmpty) {
    OnSwing.future {
      performLogin(cfg.token)
    }
  }

  private val publicIpAddress = PublicIpAddress.get(global)


  def requestUsers = {
    userApi.at(global).flatMap(api =>
      publicIpAddress.at(global).flatMap(addr =>
        api.listUsers(addr, "online")
      )
    )
  }

}
