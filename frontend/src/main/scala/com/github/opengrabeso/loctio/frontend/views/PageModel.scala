package com.github.opengrabeso.loctio
package frontend.views

import common.model.UserRow
import io.udash._

/** The form's model structure. */
case class PageModel(
  loading: Boolean,
  debug: String = null,
  settings: dataModel.SettingsModel = dataModel.SettingsModel(),
  users: Seq[UserRow] = Seq.empty,
  allUsers: Seq[String] = Seq.empty,
  error: Option[Throwable] = None
)

object PageModel extends HasModelPropertyCreator[PageModel]
