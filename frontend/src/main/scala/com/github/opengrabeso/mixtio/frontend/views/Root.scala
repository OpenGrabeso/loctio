package com.github.opengrabeso.mixtio
package frontend
package views

import com.github.opengrabeso.mixtio.rest.RestAPIClient
import routing._
import io.udash._
import io.udash.bootstrap._
import io.udash.css._
import common.css._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.ExecutionContext

object Root {

  case class PageModel(token: String = "", fullName: String = "", login: String = "")

  object PageModel extends HasModelPropertyCreator[PageModel]

  class PagePresenter(
    model: ModelProperty[PageModel],
    userContextService: services.UserContextService,
    application: Application[RoutingState]
  )(implicit ec: ExecutionContext) extends Presenter[RootState.type] {


    model.subProp(_.token).listen { t =>
      login(t)
    }

    override def handleState(state: RootState.type): Unit = {}

    def login(token: String) = {
      val userApi = RestAPIClient.api.user(token)
      userApi.name.foreach { name =>
        // TODO: receive fullName as well
        model.subProp(_.login).set(name)
        model.subProp(_.fullName).set(name)
        userContextService.login(name, token)
      }
    }

    def gotoMain() = {
      application.goTo(SelectPageState)
    }
  }


  class View(model: ModelProperty[PageModel], presenter: PagePresenter) extends ContainerView with CssView {

    import scalatags.JsDom.all._

    val header: Seq[HTMLElement] = {
      val name = model.subProp(_.fullName)
      val userId = model.subProp(_.login)

      Seq(
        div(
          //GlobalStyles.header,
          id := "header",
          table(
            tbody(
              tr(
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
      )
    }


    val footer: Seq[HTMLElement] = Seq(
      div(
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
    )

    // ContainerView contains default implementation of child view rendering
    // It puts child view into `childViewContainer`
    override def getTemplate: Modifier = div(
      // loads Bootstrap and FontAwesome styles from CDN
      UdashBootstrap.loadBootstrapStyles(),
      UdashBootstrap.loadFontAwesome(),

      BootstrapStyles.container,
      header,
      childViewContainer,
      footer
    )
  }

  class PageViewFactory(
    application: Application[RoutingState],
    userService: services.UserContextService,
  ) extends ViewFactory[RootState.type] {

    import scala.concurrent.ExecutionContext.Implicits.global

    override def create(): (View, Presenter[RootState.type]) = {
      val model = ModelProperty(PageModel())
      val presenter = new PagePresenter(model, userService, application)

      val view = new View(model, presenter)
      (view, presenter)
    }
  }

}