package com.github.opengrabeso.loctio
package frontend
package views
package select

import java.time.{Duration, ZonedDateTime}

import com.github.opengrabeso.loctio.dataModel.SettingsModel
import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.dropdown.UdashDropdown
import io.udash.bootstrap.modal.UdashModal
import io.udash.bootstrap.utils.BootstrapStyles._
import io.udash.bootstrap.table.UdashTable
import io.udash.css._
import io.udash.rest.raw.HttpErrorException
import scalatags.JsDom.all._

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
  globals: ModelProperty[SettingsModel]
) extends Headers.PageView(globals, presenter) with FinalView with CssView with PageUtils with TimeFormatting {
  val s = SelectPageStyles


  def getUserStatusIcon(state: String, time: ZonedDateTime) = {
    val displayState = state match {
      case  "online" | "busy" =>
        val now = ZonedDateTime.now()
        val age = Duration.between(time, now).toMinutes
        if (age < 10) {
          state
        } else if (age < 60) {
          "away"
        } else {
          "offline"
        }
      case _ =>
        // if user is reported as offline, do not check if the user was active recently
        // as we got a positive notification about going offline
        // note: invisible user is reporting offline as well
        state
    }

    img(
      s.stateIcon,
      src := "static/user-" + displayState + ".ico",
    )
  }

  val setLocationUser = Property[String]("")
  val setLocationAddr = Property[String]("")
  val setLocationLocation = Property[String]("")
  val locationOkButton = UdashButton(Color.Success.toProperty)(_ => Seq[Modifier](UdashModal.CloseButtonAttr, "OK"))
  buttonOnClick(locationOkButton) {
    presenter.setLocationName(setLocationUser.get, setLocationLocation.get)
  }
  val setLocationModal = UdashModal(Some(Size.Small).toProperty)(
    headerFactory = Some(_ => div("Set location name (", bind(setLocationUser), ")").render),
    bodyFactory = Some { nested =>
      div(
        Spacing.margin(),
        Card.card, Card.body, Background.color(Color.Light),
      )(
        bind(setLocationAddr),
        TextInput(setLocationLocation)()
      ).render
    },
    footerFactory = Some { _ =>
      div(
        locationOkButton.render,
        UdashButton(Color.Danger.toProperty)(_ => Seq[Modifier](UdashModal.CloseButtonAttr, "Cancel")).render
      ).render
    }
  )

  private def locationRealNameOnly(s: String): String = {
    val IpAddr = "[0-9]+\\.[0-9.]+]".r
    s match {
      case IpAddr() =>
        ""
      case _ =>
        s
    }
  }
  def userDropDown(ar: UserRow) = {
    val callback = () => {
      setLocationUser.set(ar.login)
      setLocationAddr.set(ar.location)
      setLocationLocation.set(locationRealNameOnly(ar.location))
      setLocationModal.show()
    }
    val items = SeqProperty[UdashDropdown.DefaultDropdownItem](Seq(
      UdashDropdown.DefaultDropdownItem.Button("Name location", callback),
    ))

    val dropdown = UdashDropdown.default(items)(_ => Seq[Modifier]("", Button.color(Color.Primary)))
    dropdown.render
  }

  def getTemplate: Modifier = {

    // value is a callback
    type DisplayAttrib = TableFactory.TableAttrib[UserRow]
    val attribs = Seq[DisplayAttrib](
      TableFactory.TableAttrib("", (ar, _, _) => Seq[Modifier](s.statusTd, getUserStatusIcon(ar.lastState, ar.lastTime).render)),
      TableFactory.TableAttrib("User", (ar, _, _) => ar.login.render),
      TableFactory.TableAttrib("Location", (ar, _, _) => ar.location.render),
      TableFactory.TableAttrib("Last seen", (ar, _, _) => formatDateTime(ar.lastTime.toJSDate).render),
      TableFactory.TableAttrib("", (ar, _, _) => userDropDown(ar)),
    )

    val table = UdashTable(model.subSeq(_.users), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(TableFactory.headerFactory(attribs)),
      rowFactory = TableFactory.rowFactory(attribs)
    )

    def getErrorText(ex: Throwable) = ex match {
      case he: HttpErrorException =>
        s"HTTP Error ${he.code}"
      case _ =>
        ex.toString
    }

    div(
      prefix,
      header,
      div(
        div( // this will have a border
          s.container,
          setLocationModal,
          div(
            showIfElse(model.subProp(_.loading))(
              p("Loading...").render,
              div(
                bind(model.subProp(_.error).transform(_.map(ex => s"Error ${getErrorText(ex)}").orNull)),
                table.render
              ).render
            )
          )
        ),
      ),
      footer
    )
  }
}