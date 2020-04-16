package com.github.opengrabeso.loctio
package frontend
package views

import com.github.opengrabeso.loctio.common.css.GlobalStyles
import com.github.opengrabeso.loctio.dataModel.SettingsModel
import routing._
import io.udash._
import io.udash.bootstrap._
import io.udash.css._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

object Headers {
  abstract class PagePresenter[T<: State](application: Application[RoutingState]) extends Presenter[T] {
    def gotoMain(): Unit = application.goTo(SelectPageState)

    def gotoSettings(): Unit = application.goTo(SettingsPageState)

  }
  abstract class PageView[T<: State](globals: ModelProperty[SettingsModel], presenter: PagePresenter[T]) extends CssView with PageUtils {
    import scalatags.JsDom.all._

    private val settingsButton = button("Settings".toProperty)

    buttonOnClick(settingsButton) {presenter.gotoSettings()}

    val prefix = Seq[Modifier](
      // loads Bootstrap and FontAwesome styles from CDN
      UdashBootstrap.loadBootstrapStyles(),
      UdashBootstrap.loadFontAwesome(),

      BootstrapStyles.container,

    )

    val header: HTMLElement = {
      val name = globals.subProp(_.fullName)
      val userId = globals.subProp(_.login)

      div(
        //GlobalStyles.header,
        id := "header",
        table(
          tbody(
            tr(
              td(
                settingsButton
              ),
              td(
                table(
                  tbody(
                    tr(td(a(
                      href := "/", appName,
                      onclick :+= {_: dom.Event =>
                        presenter.gotoMain()
                        true
                      }
                    ))),
                    tr(td(
                      "User:",
                      produce(userId) { s =>
                        a(href := s"https://www.github.com/$s", bind(name)).render
                      }
                    ))
                  )
                )
              )
            )
          )
        )
      ).render
    }


    val footer: HTMLElement = div(
      //GlobalStyles.footer,
      id := "footer",
      a(
        href := "http://www.github.com/",
        id := "powered_by_github",
        rel := "nofollow"
      ),
      p(
        GlobalStyles.footerText,
        " © 2020 ",
        a(
          href := s"https://github.com/OndrejSpanel/$gitHubName",
          GlobalStyles.footerLink,
        ),
        "Ondřej Španěl",
        div()
      )
    ).render


  }


}
