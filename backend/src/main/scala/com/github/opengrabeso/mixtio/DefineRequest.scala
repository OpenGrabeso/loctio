package com.github.opengrabeso.mixtio

import spark.{Request, Response, Session}

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

abstract class DefineRequest(val handleUri: String, val method: Method = Method.Get) {

  // some actions (logout) may have their URL prefixed to provide a specific functionality

  def apply(request: Request, resp: Response): AnyRef = {

    import com.google.appengine.api.utils.SystemProperty

    if (SystemProperty.environment.value() == SystemProperty.Environment.Value.Development) {
      // logging on production server is counter-productive, logs are already sorted by request
      println(s"Request ${request.url()}")
    }
    val nodes = html(request, resp)
    if (nodes.nonEmpty) {
      nodes.head match {
        case <html>{_*}</html> =>
          val docType = """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd" >"""
          docType + nodes.toString
        case _ =>
          resp.`type`("text/xml; charset=utf-8")
          val xmlPrefix = """<?xml version="1.0" encoding="UTF-8"?>""" + "\n"
          xmlPrefix + nodes.toString
      }
    } else resp
  }

  def html(request: Request, resp: Response): NodeSeq

  def cond(boolean: Boolean) (nodes: NodeSeq): NodeSeq = {
    if (boolean) nodes else Nil
  }

}
