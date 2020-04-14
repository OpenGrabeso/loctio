package com.github.opengrabeso.mixtio

import java.io.InputStream

import com.fasterxml.aalto.sax.SAXParserFactoryImpl
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
//import org.xml.sax.Attributes
//import org.xml.sax.helpers.DefaultHandler

import scala.collection.mutable.ArrayBuffer

object SAXParser {

  trait Events {
    def open(path: Seq[String])
    def read(path: Seq[String], text: String)
    def wantText: Boolean
    def close(path: Seq[String])
  }

  // reverse :: associativity so that paths can be written in a natural order
  object / {
    def unapply(arg: Seq[String]): Option[(Seq[String], String)] = {
      arg match {
        case head +: tail => Some(tail, head)
        case _ => None
      }
    }
  }

  def parse(doc: InputStream)(handler: Events) = {

    var path = List.empty[String]

    object SaxHandler extends DefaultHandler {

      val elementStack = ArrayBuffer.empty[StringBuilder]
      override def startElement(uri: String, localName: String, qName: String, attributes: Attributes) = {
        path = localName :: path
        elementStack += new StringBuilder()
        handler.open(path)
      }

      override def endElement(uri: String, localName: String, qName: String) = {
        if (path.headOption.contains(localName)) {
          val text = elementStack.remove(elementStack.size - 1)
          handler.read(path, text.mkString)
          handler.close(path)
          path = path.tail
        } else {
          throw new UnsupportedOperationException(s"Unexpected tag end `$localName`")
        }
      }

      override def characters(ch: Array[Char], start: Int, length: Int) = {
        // optimization: do not build a text string when nobody is interested about it
        if (handler.wantText) {
          elementStack.last.appendAll(ch, start, length)
        }
      }
    }

    val factory = SAXParserFactoryImpl.newInstance()
    val p = factory.newSAXParser()
    p.parse(doc, SaxHandler)
  }


  trait TagHandler {
    def open() = ()
    def text(s: String) = ()
    def close() = ()
    def wantText = false
  }

  object root {
    def apply(inner: XMLTag*): XMLTag = new XMLTag("--root--", inner:_*)
  }
  class XMLTag(val name: String, val inner: XMLTag*) extends TagHandler {

    val tagMap: Map[String, XMLTag] = inner.map(tag => tag.name -> tag)(collection.breakOut)

    def findInner(tag: String): Option[XMLTag] = tagMap.get(tag)
  }
  private class XMLTagWithOpen(name: String, openFunc: => Unit, inner: XMLTag*) extends XMLTag(name, inner:_*) {
    override def open() = openFunc
  }

  class ProcessText(name: String, process: String => Unit) extends XMLTag(name) {
    override def text(s: String) = process(s)
    override def wantText = true
  }

  implicit class DSL(name: String) {
    def text(process: String => Unit): ProcessText = new ProcessText(name, process)
    def tag(inner: XMLTag*): XMLTag = new XMLTag(name, inner:_*)
    def tagWithOpen(open: => Unit, inner: XMLTag*): XMLTag = new XMLTagWithOpen(name, open, inner:_*)
  }


  trait SAXParserWithGrammar extends Events {
    def grammar: XMLTag

    var inTags = List(grammar)
    // when closing, we need to know for which tags open was called
    var known = List.empty[Boolean]


    def open(path: Seq[String]) = {

      val descendInto = inTags.head.findInner(path.head)
      for (into <- descendInto) {
        inTags = into :: inTags
        into.open()
      }
      known = descendInto.isDefined :: known
    }

    def read(path: Seq[String], text: String) = {
      if (known.head) {
        inTags.head.text(text)
      }
    }

    def wantText = known.head && inTags.head.wantText

    def close(path: Seq[String]) = {
      if (known.head) {
        assert(inTags.head.name == path.head)
        inTags.head.close()
        inTags = inTags.tail
        assert(inTags.nonEmpty)
      }
      known = known.tail
    }


  }
}
