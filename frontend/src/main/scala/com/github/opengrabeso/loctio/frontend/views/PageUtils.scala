package com.github.opengrabeso.loctio
package frontend
package views

import io.udash.bindings.inputs.{Checkbox, InputBinding}
import io.udash.bootstrap.form.UdashInputGroup
import io.udash._
import io.udash.bootstrap.button.UdashButton
import common.css._
import io.udash.bootstrap._
import BootstrapStyles._
import io.udash.css.{CssStyle, CssView}
import scalatags.JsDom.all._

trait PageUtils extends common.Formatting with CssView {
  def buttonOnClick(button: UdashButton)(callback: => Unit): UdashButton = {
    button.listen {
      case UdashButton.ButtonClickEvent(_, _) =>
        callback
    }
    button
  }

  implicit class OnClick(button: UdashButton) {
    def onClick(callback: => Unit): UdashButton = buttonOnClick(button)(callback)
  }

  def checkbox(p: Property[Boolean]): UdashInputGroup = {
    UdashInputGroup()(
      UdashInputGroup.appendCheckbox(Checkbox(p)())
    )
  }

  def button(buttonText: ReadableProperty[String], disabled: ReadableProperty[Boolean] = false.toProperty): UdashButton = {
    UdashButton(disabled = disabled) { _ => Seq[Modifier](
      bind(buttonText),
      Spacing.margin(size = SpacingSize.Small)
    )}
  }

  def imageButton(disabled: ReadableProperty[Boolean], name: String, altName: String, color: Color = Color.Light): UdashButton = {
    UdashButton(disabled = disabled, buttonStyle = color.toProperty) { _ => Seq[Modifier](
      img(
        src := name,
        alt := altName,
        title := altName,
      )
    )}
  }

  def faIconButton(
    name: String, buttonText: ReadableProperty[String] = "".toProperty,
    color: Color = Color.Light, disabled: ReadableProperty[Boolean] = false.toProperty
  ): UdashButton = {
    UdashButton(disabled = disabled, buttonStyle = color.toProperty) { _ => Seq[Modifier](
      i(cls := "fas fa-" + name), " ", bind(buttonText)
    )}
  }
}
