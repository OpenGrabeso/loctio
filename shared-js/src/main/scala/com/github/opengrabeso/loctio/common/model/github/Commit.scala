package com.github.opengrabeso.loctio
package common.model.github

import com.github.opengrabeso.loctio.rest.github.EnhancedRestDataCompanion

case class Commit(
  url: String,
  sha: String,
  html_url: String,
  author: User,
  committer: User,
)

object Commit extends EnhancedRestDataCompanion[Commit]
