package com.github.opengrabeso.loctio

import java.io.StringReader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities.EscapeMode
import org.xhtmlrenderer.resource.XMLResource

object HTMLUtils {

  def rawXHTML(html: String): String = {
    val document = Jsoup.parse(html)
    document.outputSettings().escapeMode(EscapeMode.xhtml)
    document.outputSettings.syntax(Document.OutputSettings.Syntax.xml)
    document.html
  }

  def xhtml(html: String): String = {
    val xml = rawXHTML(html)
    val r = new StringReader(xml)
    //val is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
    try {
      XMLResource.load(r)
      xml
    } catch {
      case ex: Exception =>
        println(s"Not a valid XML, $ex")
        "<html><body>...</body></html>"
    }
  }
}
