package com.github.opengrabeso.mixtio
package frontend
package views.edit

import model._
import common.model._
import io.udash._

case class PageModel(
  loading: Boolean, activities: Seq[FileId],
  merged: Option[FileId] = None,
  events: Seq[EditEvent] = Nil,
  routeJS: Option[Seq[(Double, Double, Double, Double)]] = None
)

object PageModel extends HasModelPropertyCreator[PageModel]
