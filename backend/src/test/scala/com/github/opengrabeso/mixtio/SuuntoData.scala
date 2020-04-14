package com.github.opengrabeso.mixtio

import java.time.ZoneId

trait SuuntoData {
  protected def gpsPodMove: Option[Main.ActivityEvents] = {
    val res = getClass.getResourceAsStream("/suuntoMerge/Moveslink2/gps.sml")

    val move = moveslink2.XMLParser.parseXML("gps.sml", res, "")
    move
  }

  protected def questMove: Seq[Move] = {
    val res = getClass.getResourceAsStream("/suuntoMerge/Moveslink/quest.xml")

    val doc = moveslink.XMLParser.skipMoveslinkDoctype(res)
    val localTimeZone = ZoneId.systemDefault.toString

    val move = moveslink.XMLParser.parseXML("quest.xml", doc, localTimeZone)
    move
  }


}
