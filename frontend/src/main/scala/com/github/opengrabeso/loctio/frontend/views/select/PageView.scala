package com.github.opengrabeso.loctio
package frontend
package views
package select

import java.time.{Duration, ZonedDateTime}

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
  presenter: PagePresenter
) extends Headers.PageView(model, presenter) with FinalView with CssView with PageUtils with TimeFormatting {
  val s = SelectPageStyles


  def getUserStatusIcon(state: String, time: ZonedDateTime) = {
    val displayState = common.UserState.getEffectiveUserStatus(state, time)
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
    def callback(): Unit = {
      setLocationUser.set(ar.login)
      setLocationAddr.set(ar.location)
      setLocationLocation.set(locationRealNameOnly(ar.location))
      setLocationModal.show()
    }

    // change of login will force the table to be created again
    // transformToSeq binding is currently not needed, as change of invisibility will re-create the table
    // but it is nicer that way (and the re-creation might no longer happen in future)
    val items = model.subModel(_.settings).subProp(_.invisible).transformToSeq { invisible =>
      val current = ar.login == model.subModel(_.settings).subProp(_.login).get
      val base = Seq(
        UdashDropdown.DefaultDropdownItem.Button("Name location", callback),
      )
      if (current) base ++ Seq(
        UdashDropdown.DefaultDropdownItem.Button(if (invisible) "Make visible" else "Make invisible", () => presenter.toggleInvisible()),
      ) else base
    }

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
          div(
            bind(model.subProp(_.debug)),
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
      div(
        s.hideModals,
        setLocationModal,
      ),
      footer
    )
  }
}