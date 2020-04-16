package com.github.opengrabeso.loctio
package frontend
package views

import dataModel.SettingsModel
import routing._
import io.udash._
import io.udash.bootstrap._
import io.udash.css._
import common.css._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement

import scala.concurrent.ExecutionContext

object Root {

  case class PageModel(emptyModelNotAllowed: String = "")

  object PageModel extends HasModelPropertyCreator[PageModel]

  class PagePresenter(
    model: ModelProperty[PageModel],
    userContextService: services.UserContextService,
    application: Application[RoutingState]
  )(implicit ec: ExecutionContext) extends Headers.PagePresenter[RootState.type](application) {


    override def handleState(state: RootState.type): Unit = {}
  }


  class View(
    model: ModelProperty[PageModel], presenter: PagePresenter,
    globals: ModelProperty[SettingsModel]
  ) extends Headers.PageView[RootState.type](globals, presenter) with ContainerView with CssView with views.PageUtils {

    import scalatags.JsDom.all._

    private val settingsButton = button("Settings".toProperty)

    buttonOnClick(settingsButton) {presenter.gotoSettings()}

    // ContainerView contains default implementation of child view rendering
    // It puts child view into `childViewContainer`
    override def getTemplate: Modifier = div(
      prefix,
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

      val view = new View(model, presenter, userService.properties)
      (view, presenter)
    }
  }

}