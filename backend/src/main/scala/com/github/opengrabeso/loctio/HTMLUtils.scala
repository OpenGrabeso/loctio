package com.github.opengrabeso.loctio

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object HTMLUtils {
  def xhtml(html: String): String = {
    val document = Jsoup.parse(html)
    document.outputSettings.syntax(Document.OutputSettings.Syntax.xml)
    document.html
  }

}
