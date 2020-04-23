package com.github.opengrabeso.loctio
package common.model.github

import com.github.opengrabeso.loctio.rest.github.EnhancedRestDataCompanion

case class Assets(
  url: String,
  browser_download_url: String,
  id: Long,
  name: String,
  label: String,
  uploader: User
)

object Assets extends EnhancedRestDataCompanion[Assets]
