package com.github.opengrabeso.loctio
package frontend.views
package settings

import io.udash._

/** The form's model structure. */
case class PageModel(
  loading: Boolean,
  timezones: Seq[String] = Seq.empty,
  selectedTimezone: String = ""
)

object PageModel extends HasModelPropertyCreator[PageModel]
