package com.github.opengrabeso.mixtio

import java.io.InputStream

import common.model.Event.Sport
import MoveHeader.ActivityType._

import scala.collection.immutable.SortedMap
import java.time.ZonedDateTime
import common.Util._
import common.model._
import common.model.FileId._
import shared.Timing

object MoveslinkImport {


  def sportFromActivityType(at: MoveHeader.ActivityType): Sport = {
    at match {
      case RunningTrail => Sport.Run
      case RunningRoad => Sport.Run
      case Orienteering => Sport.Run
      case MountainBike => Sport.Ride
      case Cycling => Sport.Ride
      case Unknown => Sport.Workout
    }
  }

  def loadFromMove(fileName: String, digest: String, move: Move): Option[Main.ActivityEvents] = {
    // Move(fileName: Set[String], header: MoveHeader, streams: Map[Class[_], DataStream[_]]) {
    // ActivityEvents(id: ActivityId, events: Array[Event], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStream[_]]) {

    val laps = move.streamGet[DataStreamLap]

    val gps = move.streamGet[DataStreamGPS].getOrElse(new DataStreamGPS(SortedMap.empty[ZonedDateTime, GPSPoint]))

    val dist = move.streamGet[DataStreamDist].getOrElse(new DataStreamDist(SortedMap.empty[ZonedDateTime, Double]))

    // TODO: other attributes
    val hrStream = move.streamGet[DataStreamHR].getOrElse(new DataStreamHR(SortedMap.empty[ZonedDateTime, Int]))

    for {
      startTime <- move.startTime
      endTime <- move.endTime
      d <- dist.stream.lastOption.map(_._2)
    } yield {
      val sport = sportFromActivityType(move.header.moveType)

      /*
      // timestamp around 2017 needs 30 bits: (2017-1970)*365*24*3600 = (2017-1970)*365*24*3600
      val startInSec = startTime.getMillis / 1000
      val durationInSec = endTime.getMillis / 1000 - startInSec // 18 b is enough for 3 days - ln(3*24*3600)/ln(2) = 17.98
      val sportId = sport.id // 8b should be enough

      val startBits = 32
      val durationBits = 18
      val sportBits = 8
      */

      val id = ActivityId(FilenameId(fileName), digest, "Activity", startTime, endTime, sport, d)

      val events = Array[Event](BegEvent(id.startTime, sport), EndEvent(id.endTime))

      // TODO: avoid duplicate timestamp events
      val lapEvents = laps.toList.flatMap(_.stream.keys.map(LapEvent))

      val allEvents = (events ++ lapEvents).sortBy(_.stamp)

      Main.ActivityEvents(id, allEvents, dist, gps, Seq(hrStream))
    }
  }

  def loadSml(fileName: String, digest: String, stream: InputStream): Option[Main.ActivityEvents] = {

    implicit val timing = Timing.start()

    timing.logTime(s"Source.fromInputStream $fileName")

    val ret = moveslink2.XMLParser.parseXML(fileName, stream, digest)
    timing.logTime(s"parseXML $fileName")
    ret

  }

  def loadXml(fileName: String, digest: String, stream: InputStream, timezone: String): Seq[Move] = {
    val doc = moveslink.XMLParser.skipMoveslinkDoctype(stream) // TODO: most likely not needed with aalto

    moveslink.XMLParser.parseXML(fileName, doc, timezone)
  }

}
