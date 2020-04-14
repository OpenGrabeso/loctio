package com.github.opengrabeso.mixtio
package frontend
package views
package push

import routing.{PushPageState, RoutingState}
import io.udash._
import org.scalajs.dom

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService,
  sessionId: String
) extends ViewFactory[PushPageState] with settings_base.SettingsFactory with PageFactoryUtils {
  import scala.concurrent.ExecutionContext.Implicits.global

  private def updatePending(model: ModelProperty[PageModel]): Unit = {
    for ((pending, done, result) <- userService.api.get.push(sessionId, "").expected) {
      if (pending != Seq("")) {
        model.subProp(_.pending).set(pending)
        model.subProp(_.done).set(model.subProp(_.done).get ++ done)
      }
      if (pending.nonEmpty) {
        dom.window.setTimeout(() => updatePending(model), 1000) // TODO: once long-poll is implemented, reduce or remove the delay
      }
      result.foreach { r =>
        model.subProp(_.result).set(r)
      }
    }
  }

  override def create(): (View, Presenter[PushPageState]) = {
    val model = ModelProperty(PageModel(settings_base.SettingsModel())) // start with non-empty placeholder until real state is confirmed

    loadSettings(model.subModel(_.s), userService)

    updatePending(model)

    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}