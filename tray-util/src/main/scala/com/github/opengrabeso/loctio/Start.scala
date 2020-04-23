package com.github.opengrabeso.loctio

import java.awt.{AWTException, Desktop}
import java.awt.event.{KeyAdapter, KeyEvent, MouseAdapter, MouseEvent, MouseWheelEvent}
import java.net.URL
import java.time.{ZoneId, ZonedDateTime}
import java.time.format._
import java.util.Locale

import akka.actor.{ActorSystem, Cancellable}
import com.github.opengrabeso.loctio.common.PublicIpAddress
import com.github.opengrabeso.loctio.common.model.github.Notification
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

  def login() = {
    assert(SwingUtilities.isEventDispatchThread)
    val frame = loginFrame
    placeFrameAbove(frame, mouseLocation)
    frame.init()
    frame.open()
  }

  def requestNotifications(token: String): Unit = {
    if (notificationsSchedule != null) {
      notificationsSchedule.cancel()
      notificationsSchedule = null
    }

    def requestNextAfter(seconds: Int) = {
      notificationsSchedule = system.scheduler.scheduleOnce(Duration(seconds, duration.SECONDS)){
        requestNotifications(token)
      }(OnSwing)
    }

    userApi.at(global).flatMap(_.trayNotificationsHTML()).at(OnSwing).map { case (notificationsHTML, notifyUser, nextAfter) =>
      if (notificationsHTML != "") {
        mainFrame.addNotifications(notificationsHTML, notifyUser)
      }
      requestNextAfter(nextAfter)
    }.failed.at(OnSwing).foreach { ex =>
      requestNextAfter(60)
      println(s"Notifications failed $ex")
    }

  }
  def performLogin(token: String): Unit = {
    usersReady = false
    // if already logged in, report shutdown first so that we get complete notifications
    val after = if (cfg.token.nonEmpty) {
      sendShutdown()
    } else Future.successful(())
    after.at(OnSwing).foreach { _ =>
      loginName = ""
      server.at(global).flatMap(_.api.user(token).name).at(OnSwing).foreach { case (s, _) =>
        loginName = s
        println(s"Login done $s")
        // request users regularly
        if (updateSchedule != null) updateSchedule.cancel()
        updateSchedule = system.scheduler.schedule(Duration(0, duration.MINUTES), Duration(1, duration.MINUTES)) {
          requestUsers.at(OnSwing).foreach { case (users, tooltip) =>
            if (token == cfg.token) { // ignore any pending futures with a different token
              usersReady = true
              mainFrame.setUsers(users, tooltip)
            }
          }
        }(global)

        mainFrame.clearNotifications()
        requestNotifications(token)
      }
    }
  }

  def mouseLocation: Point = java.awt.MouseInfo.getPointerInfo.getLocation

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

  private def openWindow(): Unit = {
    // place the window a bit above the mouse - this avoid conflicting with the menu
    if (usersReady) {

      if (mainFrame.size == new Dimension(0,0)) mainFrame.pack()

      if (!mainFrame.visible) {
        placeFrameAbove(mainFrame, mouseLocation)
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

  private def refresh(hard: Boolean): Unit = {
    val after = if (hard) {
      sendShutdown()
    } else Future.successful(())
    after.at(OnSwing).foreach { _ =>
      requestNotifications(cfg.token)
    }
  }

  private def sendShutdown(): Future[Unit] = {
    userApi.at(global).flatMap(_.shutdown(rest.UserRestAPI.RestString("now")))
  }

  private def appExit() = {
    println("appExit")
    icon.foreach(Tray.remove)
    sendShutdown().at(OnSwing).foreach {_ =>
      System.exit(0)
    }
  }

  private object Tray {
    import java.awt.{TrayIcon, SystemTray, Image}
    import java.awt.event._

    import javax.swing.UIManager

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

        val openItem = new SimpleMenuItem("Open...", openWindow())
        val popup = new PopupMenu {
          contents += openItem
          contents += new SimpleMenuItem("Open web...", openWeb())
          contents += new Separator
          contents += new SimpleMenuItem("Login...", login())
          contents += new Separator
          contents += new SimpleMenuItem("Exit", appExit())
        }


        def showPopup(e: MouseEvent) = {
          openItem.enabled = usersReady
          val p = popup.peer
          p.setLocation(e.getX, e.getY)
          p.setInvoker(p)
          p.setVisible(true)
        }

        trayIcon addMouseListener new MouseAdapter {

          override def mouseClicked(e: MouseEvent) = if (e.getButton == 1) openWindow()

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
          openWindow()
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

    def userActive() = {
      println("User active")
    }
    object watchMouse extends MouseAdapter {
      override def mousePressed(e: MouseEvent) = userActive()
      override def mouseReleased(e: MouseEvent) = userActive()
      override def mouseWheelMoved(e: MouseWheelEvent) = userActive()
      override def mouseMoved(e: MouseEvent) = userActive()
    }
    object watchKeyboard extends KeyAdapter {
      override def keyPressed(e: KeyEvent) = userActive()
      override def keyReleased(e: KeyEvent) = userActive()
    }
    peer.addMouseListener(watchMouse)
    peer.addMouseMotionListener(watchMouse)
    peer.addMouseWheelListener(watchMouse)
    peer.addKeyListener(watchKeyboard)


    val users = new HtmlPanel(serverUrl)

    val notifications = new HtmlPanel(serverUrl) {
      //preferredSize= new Dimension(260, 800) // allow narrow size so that label content is wrapped if necessary
      listenTo(mouse.clicks)
      reactions += {
        case e: MouseClicked if e.peer.getButton == 1 =>
          openWindow()
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
    private def displayMessageTime(t: ZonedDateTime) = {
      common.UserState.smartAbsoluteTime(t.withZoneSameInstant(zone), fmtTime.format, fmtDate.format, fmtDayOfWeek.format)
    }

    private def replaceTime(in: String, displayFunc: ZonedDateTime => String): String = {
      def recurse(s: String): String = {
        val Time = "(?s)(.*)<time>([^<]+)<\\/time>(.*)".r
        s match {
          case Time(prefix, time, postfix) =>
            recurse(prefix + displayFunc(ZonedDateTime.parse(time)) + postfix)
          case _ =>
            s
        }
      }
      recurse(in)
    }

    def setUsers(usHTML: String, statusText: String): this.type = {
      users.html = replaceTime(usHTML, displayTime)
      reportTray(replaceTime(statusText, displayTime))
      this
    }

    def clearNotifications(): this.type = {
      notificationsList = Seq.empty
      addNotifications("", Seq.empty) // update the Swing component
      this
    }

    def addNotifications(html: String, notifyUser: Seq[String]): this.type = {

      if (html.nonEmpty) {
        notifications.html = replaceTime(html, displayMessageTime)
        pack()
      }

      // avoid flooding the notification area in case the user has many notifications
      for (n <- notifyUser) { // reverse to display oldest first
        Tray.message(n)
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
              } else {
                // refresh
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
        api.trayUsersHTML(addr, "online")
      )
    )
  }

}
