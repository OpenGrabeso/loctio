package com.github.opengrabeso.loctio

import java.awt.Desktop
import java.net.URL
import java.time.{ZoneId, ZonedDateTime}
import java.time.format._
import java.time.temporal.ChronoUnit
import java.util.Locale

import akka.actor.{ActorSystem, Cancellable}
import com.github.opengrabeso.loctio.common.PublicIpAddress
import com.github.opengrabeso.loctio.common.model.github.Notification
import com.github.opengrabeso.loctio.common.model.{LocationInfo, UserRow}
import com.github.opengrabeso.loctio.rest.github.AuthorizedAPI
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

import scala.swing.event.MouseClicked

object Start extends SimpleSwingApplication {

  implicit val system = ActorSystem()

  val exitEvent = Promise[Boolean]()

  private var cfg = Config.load
  private var loginName = ""
  private var usersReady = false
  private var updateSchedule: Cancellable = _
  private var notificationsSchedule: Cancellable = _
  private var serverUrl: String = _

  var lastNotifications =  Option.empty[String]

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

    serverFound.future.tap(_.foreach(s => serverUrl = s.url)) // TODO: make thread robust
  }

  def userApi: Future[rest.UserRestAPI] = if (cfg.token.nonEmpty) {
    server.at(global).map(_.api.user(cfg.token))
  } else {
    Future.failed(throw new NoSuchElementException("No token provided"))
  }

  def githubApi(token: String): AuthorizedAPI = rest.github.GitHubAPIClient.api.authorized("Bearer " + cfg.token)

  def login(location: Point) = {
    assert(SwingUtilities.isEventDispatchThread)
    val frame = loginFrame
    placeFrameAbove(frame, location)
    frame.init()
    frame.open()
  }

  def requestNotifications(token: String): Unit = {
    import rest.github.DataWithHeaders
    if (notificationsSchedule != null) {
      notificationsSchedule.cancel()
      notificationsSchedule = null
    }

    def requestNextAfter(seconds: Int) = {
      notificationsSchedule = system.scheduler.scheduleOnce(Duration(seconds, duration.SECONDS)){
        requestNotifications(token)
      }(OnSwing)
    }

    def requestNext(headers: DataWithHeaders.Headers) = {
      requestNextAfter(headers.xPollInterval.map(_.toInt).getOrElse(60))
    }

    githubApi(token).notifications.get(ifModifiedSince = lastNotifications.orNull).at(OnSwing).map { ns =>
      println("Notifications " + ns.data.size)
      mainFrame.addNotifications(ns.data)

      lastNotifications = ns.headers.lastModified orElse lastNotifications
      requestNext(ns.headers)

    }.failed.at(OnSwing).foreach {
      case rest.github.DataWithHeaders.HttpErrorExceptionWithHeaders(ex, headers) =>
        // expected - this mean nothing had changed and there is nothing to do
        requestNext(headers)
        if (ex.code != 304) { // // 304 is expected - this mean nothing had changed and there is nothing to do
          println(s"Notifications failed $ex")
        }
      case ex  =>
        requestNextAfter(60)
        println(s"Notifications failed $ex")

    }

  }
  def performLogin(token: String): Unit = {
    if (loginName != "") { // remove previous information
      mainFrame.setUsers(Seq.empty)
    }
    usersReady = false
    loginName = ""
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
          }
        }
      }(global)

      mainFrame.clearNotifications()
      requestNotifications(token)
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

  private def placeFrameAbove(frame: Frame, location: Point) = {
    placeFrame(frame, new Point(location.x, location.y - frame.size.height - 150))
  }

  private def openWindow(location: Point): Unit = {
    // place the window a bit above the mouse - this avoid conflicting with the menu
    if (usersReady) {

      if (mainFrame.size == new Dimension(0,0)) mainFrame.pack()

      if (!mainFrame.visible) {
        placeFrameAbove(mainFrame, location)
      }
      mainFrame.open() // open or make focused / on top (if already open)
    }
  }

  private def openWeb(): Unit = {
    Desktop.getDesktop.browse(new URL(s"https://${appName.toLowerCase}.appspot.com").toURI)
  }

  private def openWebGitHub(): Unit = {
    Desktop.getDesktop.browse(new URL(s"https://www.github.com/notifications?query=is%3Aunread").toURI)
  }

  private def appExit() = {
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
              action(java.awt.MouseInfo.getPointerInfo.getLocation)
            }
          }
          item.addActionListener(listener)
          popup.add(item)
          item
        }

        val openItem = addItem("Open...", openWindow)
        addItem("Open web...", _ => openWeb())
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

        // note: this does not work on Java 8 (see https://bugs.openjdk.java.net/browse/JDK-8146537)
        trayIcon.addActionListener { e =>
          openWindow(java.awt.MouseInfo.getPointerInfo.getLocation)
        }


        try {
          tray.add(trayIcon)
          trayIcon.setActionCommand("NotifyClicked")
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
      val text = if (state.isEmpty) appName else state
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

    def message(message: String): Unit = {
      assert(SwingUtilities.isEventDispatchThread)
      icon.foreach { i =>
        i.displayMessage(appName, message, TrayIcon.MessageType.NONE)
      }
    }

    def changeState(icon: TrayIcon, s: String): Unit = {
      OnSwing.future {
        changeStateImpl(icon, s)
      }
    }
  }

  val icon = Tray.show()

  def reportTray(message: String): Unit = {
    assert(SwingUtilities.isEventDispatchThread)

    icon.foreach(Tray.changeState(_, message))
  }

  object mainFrame extends Frame {
    assert(SwingUtilities.isEventDispatchThread)

    title = appName

    val users = new HtmlPanel(serverUrl)
    users.font = users.font.deriveFont(users.font.getSize2D * 1.2f)

    val notifications = new HtmlPanel(serverUrl) {
      //preferredSize= new Dimension(260, 800) // allow narrow size so that label content is wrapped if necessary
      listenTo(mouse.clicks)
      reactions += {
        case e: MouseClicked if e.peer.getButton == 1 =>
          openWindow(java.awt.MouseInfo.getPointerInfo.getLocation)
      }
    }

    val columns = Seq("", "User", "Location", "Last seen")
    val splitPane = new SplitPane(
      Orientation.Horizontal,
      new ScrollPane(users),
      new ScrollPane(notifications)
    ).tap { pane =>
      pane.preferredSize = new Dimension(330, 600)
      pane.resizeWeight = 0.3
    }

    contents = splitPane

    private var notificationsList = Seq.empty[Notification]

    private val loc = Locale.getDefault(Locale.Category.FORMAT)
    private val fmtTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(loc)
    private val fmtDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(loc)
    private val fmtDayOfWeek = DateTimeFormatter.ofPattern("eeee", loc) // Exactly 4 pattern letters will use the full form
    private val zone = ZoneId.systemDefault()

    private def displayTime(t: ZonedDateTime) = {
      common.UserState.smartTime(t.withZoneSameInstant(zone), fmtTime.format, fmtDate.format, fmtDayOfWeek.format)
    }


    def setUsers(us: Seq[(String, LocationInfo)]): this.type = {
      val table = common.UserState.userTable(loginName, false, us)

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

      def getUserStatusIcon(state: String) = {
        s"<img class='state-icon' src='static/user-$state.ico'></img>"
      }

      def userRowHTML(row: common.model.UserRow) = {
        //language=HTML
        s"""<tr>
           <td>${getUserStatusIcon(row.currentState)}</td>
           <td>${row.login}</td>
           <td>${row.location}</td>
           <td>${if (row.currentState != "online") displayTime(row.lastTime) else ""}</td>
           </tr>
          """
      }


      val tableHTML = //language=HTML
        s"""<html>
            <head>
            <link href="static/tray.css" rel="stylesheet" />
            </head>
            <body class="users">
              <table>
              <tr>${columns.map(c => s"<th>$c</th>").mkString}</tr>
              ${table.map(userRowHTML).mkString}
              </table>
            </body>
           </html>
        """
      users.html = tableHTML
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

    def clearNotifications(): this.type = {
      notificationsList = Seq.empty
      addNotifications(Seq.empty) // update the Swing component
      this
    }

    def addNotifications(ns: Seq[Notification]): this.type = {

      def notificationHTML(n: Notification) = {
        //language=HTML
        s"""
        <tr><td><b>${n.subject.title}</b><br/>
        ${n.repository.full_name} ${displayTime(n.updated_at)}</td></tr>
         """
      }

      notificationsList = ns ++ notificationsList

      val notificationsTable =
        //language=HTML
        s"""<html>
            <head>
            <link href="static/tray.css" rel="stylesheet" />
            </head>
            <body class="notifications">
              <table>
              ${notificationsList.map(notificationHTML).mkString}
              </table>
            </body>
           </html>
        """

      notifications.html = notificationsTable
      pack()

      // avoid flooding the notification area in case the user has many notifications
      for (n <- ns.take(5).reverse) { // reverse to display oldest first
        Tray.message(n.subject.title)
      }

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
        contents += new Label("Enter your GitHub token (scopes repo and notifications needed):")
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
