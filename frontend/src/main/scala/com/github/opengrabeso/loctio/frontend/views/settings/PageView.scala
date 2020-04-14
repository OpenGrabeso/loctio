package com.github.opengrabeso.loctio
package frontend
package views
package settings

import java.time.{ZoneId, ZonedDateTime}

import common.css.SelectPageStyles
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.form.{UdashForm, UdashInputGroup}
import io.udash.component.ComponentId
import io.udash.css._


class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView with PageUtils with settings_base.SettingsView {
  val s = SelectPageStyles

  import scalatags.JsDom.all._

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Submit")

  buttonOnClick(submitButton){presenter.submit()}

  def getTemplate: Modifier = {

    div(
      s.settingsContainer,
      s.limitWidth,
      template(model.subModel(_.s), presenter),
      submitButton
    )
  }
}