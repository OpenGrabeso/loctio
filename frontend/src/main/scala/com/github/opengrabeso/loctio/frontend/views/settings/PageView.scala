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

import scala.concurrent.ExecutionContext.Implicits.global

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

    val hourModifiers = Seq[Modifier](Flex.grow0(), attr("min") := "0", attr("max") := "24")
    val minuteModifiers = Seq[Modifier](Flex.grow0(), attr("min") := "0", attr("max") := "60")

    div(
      prefix,
      header,
      h1("Settings"),


      div(Flex.row(),Flex.grow0(),
        "Available hours from ",
        NumberInput(userSettings.subProp(_.visibleHoursFrom).asString)(hourModifiers),
        ":",
        NumberInput(userSettings.subProp(_.visibleMinutesFrom).asString)(minuteModifiers),
        " to ",
        NumberInput(userSettings.subProp(_.visibleHoursTo).asString)(hourModifiers),
        ":",
        NumberInput(userSettings.subProp(_.visibleMinutesTo).asString)(minuteModifiers),
        " timezone",
        TextInput(userSettings.subProp(_.timezone))(),
        button("Autodetect timezone".toProperty).tap(buttonOnClick(_){
          presenter.guessTimezone()
        }),
        div(Flex.grow1())
      ),
      div(
        Flex.row(),Flex.grow0(),
        div(
          Flex.column(),
          button("Submit".toProperty).tap(buttonOnClick(_) {
            presenter.submit().foreach(_ => presenter.gotoMain())
          }),
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