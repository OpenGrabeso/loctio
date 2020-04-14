package com.github.opengrabeso.mixtio
package frontend
package views
package push

import common.css._
import io.udash._
import io.udash.bootstrap.button.UdashButton
import io.udash.component.ComponentId
import io.udash.css._
import scalatags.JsDom.all._

class PageView(
  model: ModelProperty[PageModel],
  presenter: PagePresenter,
) extends FinalView with CssView with PageUtils with settings_base.SettingsView with ActivityLink {
  val s = SelectPageStyles
  val ss = SettingsPageStyles

  private val submitButton = UdashButton(componentId = ComponentId("about"))(_ => "Proceed...")

  buttonOnClick(submitButton){presenter.gotoSelect()}

  def getTemplate: Modifier = {

    def produceList(seq: ReadableSeqProperty[String], title: String) = {
      produce(seq) { file =>
        if (file.nonEmpty) {
          div(
            ss.container,
            ss.flexItem,
            table(
              tr(th(h2(title))),
              file.map(file =>
                tr(td(niceFileName(file)))
              )
            )
          ).render
        } else {
          div().render
        }
      }
    }

    div(
      ss.flexContainer,
      div(
        ss.container,
        ss.flexItem,
        template(model.subModel(_.s), presenter),
        showIf(model.subSeq(_.pending).transform(_.isEmpty))(submitButton.render),
        div(h3(bind(model.subProp(_.result)))).render
      ),

      produceList(model.subSeq(_.pending), "Uploading:"),
      produceList(model.subSeq(_.done), "Uploaded:")
    )
  }
}