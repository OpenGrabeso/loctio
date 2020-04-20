package com.github.opengrabeso.loctio
package common.model.github

import rest.EnhancedRestDataCompanion

@SerialVersionUID(11L)
case class ArticleContent(id: ArticleId, title: String, content: String) {

  def link: String = id.link

  def shortName: String = {
    common.Formatting.shortNameString(title)
  }
}

object ArticleContent extends EnhancedRestDataCompanion[ArticleContent]
