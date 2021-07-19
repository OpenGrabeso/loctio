package com.github.opengrabeso.loctio

import java.net.{URLDecoder, URLEncoder}
import Main._

import javax.servlet.{ServletRequest, ServletResponse}
import scala.util.Try
import scala.xml.NodeSeq
import ServletUtils._

import java.nio.charset.StandardCharsets

import scala.xml.NodeSeq

sealed trait Method
object Method {
  case object Get extends Method
  case object Put extends Method
  case object Post extends Method
  case object Delete extends Method

}

case class Handle(value: String, method: Method = Method.Get)

object DefineRequest {
  abstract class Post(handleUri: String) extends DefineRequest(handleUri, method = Method.Post)
}

abstract class DefineRequest(val handleUri: String, val method: Method = Method.Get) extends ServletUtils {
  type Request = ServletRequest
  type Response = ServletResponse

  def uriRest(request: ServletRequest): String = {
    val uri = request.url
    if (handleUri.endsWith("*")) {
      val prefix = handleUri.dropRight(1)
      assert(uri.startsWith(prefix))
      uri.drop(prefix.length)
    } else {
      throw new UnsupportedOperationException(s"Cannot get URI rest by pattern $handleUri")
    }

  }

  // some actions (logout) may have their URL prefixed to provide a specific functionality

  def handle(request: Request, resp: Response): Unit = {

    val nodes = html(request, resp)
    if (nodes.nonEmpty) {
      nodes.head match {
        case <html>{_*}</html> =>
          val docType = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd" >"""
          val body = docType + nodes.toString
          resp.`type`("text/html")
          resp.getOutputStream.write(body.getBytes(StandardCharsets.UTF_8))
        case _ =>
          resp.setContentType("text/xml; charset=utf-8")
          val xmlPrefix = """<?xml version="1.0" encoding="UTF-8"?>""" + "\n"
          val body = xmlPrefix + nodes.toString
          resp.getOutputStream.write(body.getBytes(StandardCharsets.UTF_8))
          resp.`type`("text/xml")
      }
    }
  }


  def html(request: Request, resp: Response): NodeSeq

  def cond(boolean: Boolean) (nodes: NodeSeq): NodeSeq = {
    if (boolean) nodes else Nil
  }

}
