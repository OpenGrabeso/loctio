package com.github.opengrabeso.mixtio
package frontend
package views.edit
import com.github.opengrabeso.mixtio.facade.UdashApp
import common.model._
import model._
import routing.{EditPageState, RoutingState}
import io.udash._

/** Prepares model, view and presenter for demo view. */
class PageViewFactory(
  application: Application[RoutingState],
  userService: services.UserContextService,
  activities: Seq[FileId]
) extends ViewFactory[EditPageState] {
  import scala.concurrent.ExecutionContext.Implicits.global

  override def create(): (View, Presenter[EditPageState]) = {
    val model = ModelProperty(PageModel(loading = true, activities))

    for {
      mergedId <- userService.api.get.mergeActivitiesToEdit(activities, UdashApp.sessionId)
    } {
      val activity = mergedId.map(_._1)
      val events = mergedId.toSeq.flatMap(_._2)
      model.subProp(_.merged).set(activity)
      if (events.nonEmpty) {
        val first = events.head
        model.subProp(_.events).set {
          events.map { case (event, dist) =>
            EditEvent(first._1.stamp, event, dist)
          }
        }
        for (routeJs <- userService.api.get.routeData(activity.get)) {
          model.subProp(_.routeJS).set(Some(routeJs))
        }
      } else {
        model.subProp(_.events).set(Nil)
      }
      model.subProp(_.loading).set(false)
    }


    val presenter = new PagePresenter(model, userService, application)
    val view = new PageView(model, presenter)
    (view, presenter)
  }
}