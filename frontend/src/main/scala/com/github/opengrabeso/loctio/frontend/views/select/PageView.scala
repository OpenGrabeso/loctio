package com.github.opengrabeso.loctio
package frontend
package views
package select

import java.time.{Duration, ZonedDateTime}


import com.github.opengrabeso.loctio.dataModel.SettingsModel
import common.css._
import io.udash._
import io.udash.bootstrap.table.UdashTable
import io.udash.css._
import scalatags.JsDom.all._

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
  globals: ModelProperty[SettingsModel]
) extends FinalView with CssView with PageUtils with TimeFormatting {
  val s = SelectPageStyles


  def getUserStatusIcon(time: ZonedDateTime) = {
    val now = ZonedDateTime.now()
    val age = Duration.between(time, now).toMinutes
    val state = if (age < 10) {
      "online"
    } else if (age < 60) {
      "away"
    } else {
      "offline"
    }

    img(
      s.stateIcon,
      src := "static/user-" + state + ".ico",
    )
  }

  def getTemplate: Modifier = {


    // value is a callback
    type DisplayAttrib = TableFactory.TableAttrib[UserRow]
    val attribs = Seq[DisplayAttrib](
      TableFactory.TableAttrib("", (ar, _, _) => Seq[Modifier](s.statusTd, getUserStatusIcon(ar.lastTime).render)),
      TableFactory.TableAttrib("User", (ar, _, _) => ar.login.render),
      TableFactory.TableAttrib("Location", (ar, _, _) => ar.location.render),
      TableFactory.TableAttrib("Last seen", (ar, _, _) => formatDateTime(ar.lastTime.toJSDate).render),
    )

    val table = UdashTable(model.subSeq(_.users), striped = true.toProperty, bordered = true.toProperty, hover = true.toProperty, small = true.toProperty)(
      headerFactory = Some(TableFactory.headerFactory(attribs)),
      rowFactory = TableFactory.rowFactory(attribs)
    )

    div(
      s.container,
      div(
        showIfElse(model.subProp(_.loading))(
          p("Loading...").render,
          div(
            bind(model.subProp(_.error).transform(_.map(ex => s"Error loading activities ${ex.toString}").orNull)),
            table.render
          ).render
        )
      )
    )
  }
}