package com.github.opengrabeso.loctio

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import com.github.opengrabeso.loctio.common.PublicIpAddress
import com.github.opengrabeso.loctio.common.model.LocationInfo
import javax.swing.event.TableModelListener
import javax.swing.table.{AbstractTableModel, TableModel}
import rest.{RestAPI, RestAPIClient}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise, duration}
import scala.swing._
import scala.util.{Failure, Success}
import scala.swing.Swing._
import shared.ChainingSyntax._

import scala.concurrent.ExecutionContext.global
import shared.FutureAt._

import scala.collection.mutable
import scala.collection.parallel.immutable

object Start extends SimpleSwingApplication {

  // consider sharing with frontend (Udash) frontend.views.select
  case class UserRow(login: String, location: String, lastTime: ZonedDateTime, lastState: String)

  implicit val system = ActorSystem()

  val exitEvent = Promise[Boolean]()

  private var cfg = Config.load
  private var loginName = Option.empty[Future[String]]

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

  def userApi: Future[rest.UserRestAPI] = server.at(global).map(_.api.user(cfg.token))

  def login(location: Point) = {
    val frame = loginFrame
    placeFrameAbove(frame, location)
    frame.open()
  }

  def performLogin(token: String): Unit = {
    loginName = Some(server.at(global).flatMap(_.api.user(token).name).at(global).map(_._1).tap(_.at(global).onComplete(s => println(s"Login done $s"))))
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
    placeFrame(frame, new Point(location.x, location.y - frame.size.height - frame.preferredSize.height * 2))
  }

  def openWindow(location: Point): Unit = {
    // place the window a bit above the mouse - this avoid conflicting with the menu
    placeFrameAbove(mainFrame, location)
    usersReady.future.at(OnSwing).foreach {_ =>
      mainFrame.open()
    }
  }

  def appExit() = {
    println("appExit")
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
        val is = getClass.getResourceAsStream("/user-online.ico")

        val image = ImageIO.read(is)

        val imageSized = image.getScaledInstance(iconSize.width, iconSize.height, Image.SCALE_SMOOTH)
        val trayIcon = new TrayIcon(imageSized, appName)

        import java.awt.event.MouseAdapter

        val popup = new JPopupMenu

        def addItem(title: String, action: Point => Unit) = {
          val item = new JMenuItem(title)
          object listener extends ActionListener {
            def actionPerformed(e: ActionEvent) = {
              action(MouseInfo.getPointerInfo.getLocation)
            }
          }
          item.addActionListener(listener)
          popup.add(item)
        }

        addItem("Open...", openWindow)
        popup.addSeparator()
        addItem("Login...", login)
        popup.addSeparator()
        addItem("Exit", _ => appExit())

        def showPopup(e: MouseEvent) = {
          popup.setLocation(e.getX, e.getY)
          popup.setInvoker(popup)
          popup.setVisible(true)
        }

        trayIcon addMouseListener new MouseAdapter {

          override def mouseClicked(e: MouseEvent) = if (e.getClickCount > 1) openWindow(new Point(e.getPoint)) // note: any button double click opens the window

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
      val text = if (state.isEmpty) title else title + ": " + state
      icon.setToolTip(text)
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

  object mainFrame extends Frame {
    title = appName

    val columns = Seq("", "User", "Location", "Last seen")
    var users = Vector.empty[UserRow]

    implicit class IndexUserRow(row: UserRow) {
      private val get = IndexedSeq[() => String](
        () => row.lastState,
        () => row.login,
        () => row.location,
        () => row.lastTime.toString
      )
      def apply(index: Int): String = get(index)()
    }

    object tableModel extends AbstractTableModel {
      def getRowCount = users.size
      def getColumnCount = columns.size
      override def getColumnName(columnIndex: Int) = columns(columnIndex)
      def getValueAt(rowIndex: Int, columnIndex: Int) = users(rowIndex)(columnIndex)
    }
    val table = new Table(tableModel)

    contents = new FlowPanel {
      contents += new Label("Presence and location utility:")
      contents += table
    }

    def setUsers(us: Seq[(String, LocationInfo)]): this.type = {
      users = us.map { u =>
        UserRow(u._1, u._2.location, u._2.lastSeen, u._2.state)
      }.toVector
      pack()
      this
    }
  }


  override def top = mainFrame

  object loginFrame extends Frame { dialog =>
    title = appName
    private val tokenField = new TextField("")
    contents = new BoxPanel(Orientation.Vertical) {
      for {
        n <- loginName
        v <- n.value
      } {
        contents += new BoxPanel(Orientation.Horizontal) {
          contents += new Label(s"Currently logged in as $v")
        }
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

  performLogin(cfg.token)

  private val publicIpAddress = PublicIpAddress.get(global)

  val usersReady = Promise[Unit]()

  def requestUsers = {
    userApi.at(global).flatMap(api =>
      publicIpAddress.at(global).flatMap(addr =>
        api.listUsers(addr, "online")
      )
    )
  }

  // request users regularly
  system.scheduler.schedule(Duration(0, duration.MINUTES), Duration(1, duration.MINUTES)){
    requestUsers.at(OnSwing).foreach { users =>
      usersReady.trySuccess(())
      mainFrame.setUsers(users)
    }
  }(global)

}
