package com.github.opengrabeso.loctio
package frontend.routing

import common.model._
import io.udash._

/**
  * add any new state into:
-  [StatesToViewFactoryDef]
-  [RoutingRegistryDef]
  */

sealed abstract class RoutingState() extends State {
  override type HierarchyRoot = RoutingState
  def parentState = None
}

case object SelectPageState extends RoutingState
case object SettingsPageState extends RoutingState