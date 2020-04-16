package com.github.opengrabeso.loctio.frontend
package routing

import views._
import io.udash._

class StatesToViewFactoryDef extends ViewFactoryRegistry[RoutingState] {
  def matchStateToResolver(state: RoutingState): ViewFactory[_ <: RoutingState] =
    state match {
      case SelectPageState => new select.PageViewFactory(ApplicationContext.application, ApplicationContext.userContextService)
    }
}