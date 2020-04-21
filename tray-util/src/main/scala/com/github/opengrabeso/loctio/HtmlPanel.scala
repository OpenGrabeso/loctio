package com.github.opengrabeso.loctio

import java.io.ByteArrayInputStream

import org.xhtmlrenderer.simple.XHTMLPanel
import org.xhtmlrenderer.swing.NaiveUserAgent

import scala.swing.Panel

class HtmlPanel extends Panel {
  lazy val uac = new NaiveUserAgent
  override lazy val peer: XHTMLPanel = new XHTMLPanel(uac) with SuperMixin

  def html: String = throw new UnsupportedOperationException("HTML document is write only")
  def html_=(text: String): Unit = {
    val is = new ByteArrayInputStream(text.getBytes)
    val url = "loctio://" // invalid URL
    peer.setDocument(is, url)
  }
}
