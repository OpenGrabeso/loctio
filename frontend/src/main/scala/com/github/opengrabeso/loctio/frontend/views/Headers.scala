package com.github.opengrabeso.loctio
package frontend
package views

import com.github.opengrabeso.loctio.common.css.GlobalStyles
import routing._
import io.udash._
import io.udash.bootstrap._
import io.udash.bootstrap.button.UdashButton
import io.udash.bootstrap.modal.UdashModal
import io.udash.bootstrap.utils.BootstrapStyles._
import io.udash.css._
import org.scalajs.dom
import org.scalajs.dom.Node
import org.scalajs.dom.HTMLElement

object Headers {
  abstract class PagePresenter[T<: State](application: Application[RoutingState]) extends Presenter[T] {
    def gotoMain(): Unit = application.goTo(SelectPageState)
    def gotoPreferences() = application.goTo(SettingsPageState)
  }
  abstract class PageView[T<: State](presenter: PagePresenter[T]) extends CssView with PageUtils {
    def onAdminClick(): Unit

    import scalatags.JsDom.all._

    protected val globals = ApplicationContext.settings

    private val settingsToken = Property[String]("")
    private val settingsOkButton = UdashButton(
      options = Color.Success.option, disabled = settingsToken.transform(_.isEmpty)
    )(_ => Seq[Modifier](UdashModal.CloseButtonAttr, "OK"))
    buttonOnClick(settingsOkButton) {
      globals.subProp(_.token).set(settingsToken.get)
      dataModel.SettingsModel.store(globals.get)
    }
    private val settingsModal = UdashModal(Some(Size.Small).toProperty)(
      headerFactory = Some(_ => div("Enter your GitHub token").render),
      bodyFactory = Some { nested =>
        div(
          Spacing.margin(),
          Card.card, Card.body, Background.color(Color.Light),
        )(
          "Currently logged in as ", bind(globals.subProp(_.login)),
          TextInput(settingsToken)()
        ).render
      },
      footerFactory = Some { _ =>
        div(
          settingsOkButton.render,
          UdashButton(options = Color.Danger.option)(_ => Seq[Modifier](UdashModal.CloseButtonAttr, "Cancel")).render
        ).render
      }
    )

    private val settingsButton = button("Log in".toProperty)
    private val preferencesButton = faIconButton("cog", "Settings".toProperty)
    private val adminButton = faIconButton("cogs", "Site administration".toProperty)

    buttonOnClick(settingsButton) {
      settingsToken.set("")
      settingsModal.show()
    }

    buttonOnClick(adminButton) {
      onAdminClick()
    }
    buttonOnClick(preferencesButton) {
      presenter.gotoPreferences()
    }

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
        Display.flex(),
        Flex.row(),
        //GlobalStyles.header,
        id := "header",
        settingsModal,
        settingsButton,
        div(
          Display.flex(),
          Flex.column(),
          a(
            href := "/", appName,
            onclick :+= {_: dom.Event =>
              presenter.gotoMain()
              true
            }
          ),
          div(
            "User: ",
            produce(userId) { s =>
              a(href := s"https://www.github.com/$s", bind(name)).render
            }
          ),
        ),
        div(Flex.grow1()),
        td(preferencesButton).render,
        showIf(globals.subProp(_.role).transform(_ == "admin")) (
          Seq[Node](
            td(adminButton).render
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
        " © 2020 - 2023",
        a(
          href := s"https://github.com/gamatron/$gitHubName",
          GlobalStyles.footerLink,
          "Ondřej Španěl",
        ),
        div()
      )
    ).render


  }


}
