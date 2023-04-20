package com.github.opengrabeso.loctio
package common.model

import com.avsystem.commons.rpc.AsRawReal
import io.udash.rest.raw.{HttpBody, IMapping, PlainValue, RestResponse}

case class HtmlData(data: String)

// text encoding

object HtmlData extends rest.EnhancedRestDataCompanion[HtmlData] {
  implicit val rawReal: AsRawReal[RestResponse, HtmlData] = AsRawReal.create(
    real => RestResponse(200, IMapping.create[PlainValue](), HttpBody.textual(real.data, mediaType = "text/html")),
    raw => HtmlData(raw.body.readText())
  )

}

case class CssData(data: String)

object CssData extends rest.EnhancedRestDataCompanion[CssData] {
  implicit val rawReal: AsRawReal[RestResponse, CssData] = AsRawReal.create(
    real => RestResponse(200, IMapping.create[PlainValue](), HttpBody.textual(real.data, mediaType = "text/css")),
    raw => CssData(raw.body.readText())
  )

}
