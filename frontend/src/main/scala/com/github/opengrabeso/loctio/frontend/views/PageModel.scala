package com.github.opengrabeso.loctio.frontend.views

import com.github.opengrabeso.loctio.frontend.views.select.UserRow
import io.udash._

/** The form's model structure. */
case class PageModel(loading: Boolean, users: Seq[UserRow] = Seq.empty, error: Option[Throwable] = None)

object PageModel extends HasModelPropertyCreator[PageModel]
