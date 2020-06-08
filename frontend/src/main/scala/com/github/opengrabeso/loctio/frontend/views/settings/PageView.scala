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

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter
) extends Headers.PageView(model, presenter) with FinalView with CssView with PageUtils with TimeFormatting {
  val s = SelectPageStyles
  def onAdminClick() = {}
  def getTemplate = {
    div(
      prefix,
      header,
      h1("Settings"),

      div(
        Flex.row(),
        div(
          Flex.column(),
          button("Submit".toProperty).tap(buttonOnClick(_)(presenter.gotoMain())),
        ),
        div(
          Flex.column()
        )
      ),
      footer
    )
  }
}