package com.github.opengrabeso.loctio

import java.io.StringReader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities.EscapeMode
import org.xhtmlrenderer.resource.XMLResource

object HTMLUtils {

  def rawXHTML(html: String): (String, String) = {
    val document = Jsoup.parse(html)
    document.outputSettings().escapeMode(EscapeMode.xhtml)
    document.outputSettings.syntax(Document.OutputSettings.Syntax.xml)
    // parse adds html and body elements - we are interested in the body content only
    (document.body().html, document.html)
  }

  def xhtml(html: String): String = {
    val (xmlBody, xmlDoc) = rawXHTML(html)
    // verify we can parse the result
    val r = new StringReader(xmlDoc)
    //val is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
    try {
      XMLResource.load(r)
      xmlBody
    } catch {
      case ex: Exception =>
        println(s"Not a valid XML, $ex")
        "<p>...</p>"
    }
  }
}
