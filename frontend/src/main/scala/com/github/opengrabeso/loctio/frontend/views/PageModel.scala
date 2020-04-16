package com.github.opengrabeso.loctio
package frontend.views

import io.udash._

/** The form's model structure. */
case class PageModel(
  loading: Boolean,
  debug: String = null,
  settings: dataModel.SettingsModel = dataModel.SettingsModel(),
  users: Seq[select.UserRow] = Seq.empty,
  error: Option[Throwable] = None
)

object PageModel extends HasModelPropertyCreator[PageModel]
