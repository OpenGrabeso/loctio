package com.github.opengrabeso.loctio
package frontend
package views.select

import frontend.views.PageModel
import rest.RestAPI
import routing._
import io.udash._

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  rpc: RestAPI
) extends ViewFactory[SelectPageState.type] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[SelectPageState.type]) = {
    val model = ModelProperty(PageModel(loading = true))

    val presenter = new PagePresenter(model, application, rpc)
    val view = new PageView(model, presenter)

    (view, presenter)
  }
}