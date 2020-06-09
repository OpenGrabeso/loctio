package com.github.opengrabeso.loctio
package frontend
package views
package settings

import common.ChainingSyntax._
import common.css._
import io.udash._
import io.udash.bootstrap.utils.BootstrapStyles._
import io.udash.css._
import scalatags.JsDom.all._

import scala.util.Try

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter
) extends Headers.PageView(model, presenter) with FinalView with CssView with PageUtils with TimeFormatting {
  val s = SelectPageStyles
  def onAdminClick() = {}
  def getTemplate = {
    val userSettings = ApplicationContext.serverSettings

    implicit class AsString(intProp: Property[Int]) {
      def asString: Property[String] = intProp.transform(_.toString, s => Try(s.toInt).getOrElse(0))
    }

    div(
      prefix,
      header,
      h1("Settings"),


      div(Flex.row(),Flex.grow0(),
        "Available hours: ",
        TextInput(userSettings.subProp(_.visibleHoursFrom).asString)(Flex.grow0()),
        ":",
        TextInput(userSettings.subProp(_.visibleMinutesFrom).asString)(Flex.grow0()),
        "...",
        TextInput(userSettings.subProp(_.visibleHoursTo).asString)(Flex.grow0()),
        ":",
        TextInput(userSettings.subProp(_.visibleMinutesTo).asString)(Flex.grow0()),
        div(Flex.grow1())
      ),
      div(
        Flex.row(),Flex.grow0(),
        div(
          Flex.column(),
          button("Submit".toProperty).tap(buttonOnClick(_)(presenter.gotoMain())),
        ),
        div(
          Flex.column()
        )
      ),
      div(Flex.row(), Flex.grow1()),
      footer
    )
  }
}