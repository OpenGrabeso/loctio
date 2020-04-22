package com.github.opengrabeso.loctio

import java.awt.Desktop
import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.charset.Charset

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
  override lazy val peer: XHTMLPanel = new XHTMLPanel(uac) with SuperMixin {
    override def setDocumentRelative(filename: String) = {
      // clicking on any link (including into loctio itself) should open a browser by default
      Desktop.getDesktop.browse(new URL(filename).toURI)
    }
  }

  def html: String = throw new UnsupportedOperationException("HTML document is write only")
  def html_=(text: String): Unit = {
    val is = new ByteArrayInputStream(text.getBytes(Charset.forName("UTF-8")))
    val url = baseUri
    peer.setDocument(is, url + "/")
  }
}
