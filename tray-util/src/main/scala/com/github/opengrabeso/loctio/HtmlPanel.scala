package com.github.opengrabeso.loctio

import java.io.ByteArrayInputStream

import org.xhtmlrenderer.simple.XHTMLPanel
import org.xhtmlrenderer.swing.NaiveUserAgent

import scala.swing.Panel

object HtmlPanel {
  class UserAgent(baseUri: String) extends NaiveUserAgent {
    assert(baseUri != null)
  }
}

import HtmlPanel._

class HtmlPanel(baseUri: String) extends Panel {
  lazy val uac = new UserAgent(baseUri)
  override lazy val peer: XHTMLPanel = new XHTMLPanel(uac) with SuperMixin

  def html: String = throw new UnsupportedOperationException("HTML document is write only")
  def html_=(text: String): Unit = {
    val is = new ByteArrayInputStream(text.getBytes)
    val url = baseUri
    peer.setDocument(is, url + "/")
  }
}
