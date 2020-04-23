package com.github.opengrabeso.loctio
package common.model.github

import java.time.ZonedDateTime

import com.github.opengrabeso.loctio.rest.github.EnhancedRestDataCompanion

// https://developer.github.com/v3/repos/releases/#get-a-single-release
case class Release(
  url: String,
  html_url: String,
  assets_url: String,
  id: Long,
  created_at: ZonedDateTime,
  published_at: ZonedDateTime,
  tag_name: String,
  name: String,
  body: String,
  author: User,
  assets: Seq[Assets],
)

object Release extends EnhancedRestDataCompanion[Release]