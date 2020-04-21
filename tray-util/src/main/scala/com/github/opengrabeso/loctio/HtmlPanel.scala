package com.github.opengrabeso.loctio

import java.io.ByteArrayInputStream

import org.xhtmlrenderer.simple.XHTMLPanel
import org.xhtmlrenderer.swing.NaiveUserAgent

import scala.swing.Panel

object HtmlPanel {
  class UserAgent(baseUri: String) extends NaiveUserAgent {
    assert(baseUri != null)
    private def serverUri(x: String) = baseUri + "/static/" + x
    override def openStream(uri: String) = {
      // allow only whitelisted resource
      val Icons = "user-[a-z]+\\.ico".r
      uri match {
        case "tray.css" =>
          super.openStream(serverUri(uri))
        case Icons() =>
          super.openStream(serverUri(uri))
        case _ =>
          super.openStream(uri) // TODO: disable
          //throw new IllegalAccessError("Resource $uri is not whitelisted")
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
