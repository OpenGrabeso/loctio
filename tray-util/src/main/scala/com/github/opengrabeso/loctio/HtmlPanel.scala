package com.github.opengrabeso.loctio

import java.io.ByteArrayInputStream

import org.xhtmlrenderer.simple.XHTMLPanel
import org.xhtmlrenderer.swing.NaiveUserAgent

import scala.swing.Panel

object HtmlPanel {
  class UserAgent(baseUri: String) extends NaiveUserAgent {
    assert(baseUri != null)
    private def serverUri(x: String) = baseUri + "/static/" + x

    override def resolveURI(uri: String) = {
      // allow only whitelisted resources
      val Icons = "user-[a-z]+\\.ico".r
      uri match {
        case "tray.css" =>
          serverUri(uri)
        case Icons() =>
          serverUri(uri)
        case _ =>
          null
      }
    }
  }
}

import HtmlPanel._

class HtmlPanel(baseUri: String) extends Panel {
  lazy val uac = new UserAgent(baseUri)
  override lazy val peer: XHTMLPanel = new XHTMLPanel(uac) with SuperMixin

  def html: String = throw new UnsupportedOperationException("HTML document is write only")
  def html_=(text: String): Unit = {
    val is = new ByteArrayInputStream(text.getBytes)
    val url = "loctio://" // invalid URL
    peer.setDocument(is, url)
  }
}
