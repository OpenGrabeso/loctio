package com.github.opengrabeso.mixtio
package moveslink

import Main.ActivityEvents
import common.Util._

import scala.annotation.tailrec

object MovesLinkUploader {

  private def autodetectSport(data: ActivityEvents): ActivityEvents = {
    // TODO: use differences from data.dist.stream instead of computing data.gps.distStream
    val speedStream = DataStreamGPS.computeSpeedStream(data.gps.distStream)

    val speedStats = DataStreamGPS.speedStats(speedStream)

    val detectSport = Main.detectSportBySpeed(speedStats, data.id.sportName)

    data.copy(id = data.id.copy(sportName = detectSport))

  }

  @tailrec
  private def processTimelinesRecurse(lineGPS: List[ActivityEvents], lineHRD: List[ActivityEvents], processed: List[ActivityEvents]): List[ActivityEvents] = {
    def prependNonEmpty(move: Option[ActivityEvents], list: List[ActivityEvents]): List[ActivityEvents] = {
      move.find(!_.isAlmostEmpty(30)).toList ++ list
    }

    if (lineGPS.isEmpty) {
      if (lineHRD.isEmpty) {
        processed
      } else {
        // HR moves without GPS info
        processTimelinesRecurse(lineGPS, lineHRD.tail, prependNonEmpty(lineHRD.headOption, processed))
      }
    } else if (lineHRD.isEmpty) {
      processTimelinesRecurse(lineGPS.tail, lineHRD, prependNonEmpty(lineGPS.headOption, processed))
    } else {
      val hrdMove = lineHRD.head
      val gpsMove = lineGPS.head

      val gpsBeg = gpsMove.startTime
      val gpsEnd = gpsMove.endTime

      val hrdBeg = hrdMove.startTime
      val hrdEnd = hrdMove.endTime

      if (gpsBeg >= hrdEnd) {
        // no match for hrd
        processTimelinesRecurse(lineGPS, lineHRD.tail, prependNonEmpty(lineHRD.headOption, processed))
      } else if (hrdBeg > gpsEnd) {
        processTimelinesRecurse(lineGPS.tail, lineHRD, prependNonEmpty(lineGPS.headOption, processed))
      } else {
        // some overlap, handle it
        // check if the activity start is the same within a tolerance

        // 10 percent means approx. 5 minutes from 1 hour (60 minutes)
        val tolerance = (lineGPS.head.duration max lineHRD.head.duration) * 0.10f

        if (timeDifference(gpsBeg, hrdBeg).abs <= tolerance) {
          // same beginning - drive by HRD
          // use from GPS only as needed by HRD
          // if GPS is only a bit longer than HDR, use it whole, unless there is another HDR waiting for it
          val (takeGPS, leftGPS) = if (timeDifference(gpsEnd, hrdEnd).abs <= tolerance && lineHRD.tail.isEmpty) {
            (Some(gpsMove), None)
          } else {
            gpsMove.span(hrdEnd)
          }

          val merged = takeGPS.map(m => (m.gps, m)).map { sm =>
            val data = sm._2.merge(hrdMove)

            data
          }

          println(s"Merged GPS ${takeGPS.map(_.toLog)} into ${hrdMove.toLog}")

          processTimelinesRecurse(prependNonEmpty(leftGPS, lineGPS.tail), prependNonEmpty(merged, lineHRD.tail), processed)
        } else if (gpsBeg > hrdBeg) {
          val (takeHRD, leftHRD) = hrdMove.span(gpsBeg)

          processTimelinesRecurse(lineGPS, prependNonEmpty(leftHRD, lineHRD.tail), prependNonEmpty(takeHRD, processed))

        } else {
          val (takeGPS, leftGPS) = gpsMove.span(hrdBeg)

          processTimelinesRecurse(prependNonEmpty(leftGPS, lineGPS.tail), lineHRD, prependNonEmpty(takeGPS, processed))
        }
      }

    }
  }

  def processTimelines(lineGPS: List[ActivityEvents], lineHRD: List[ActivityEvents]): List[ActivityEvents] = {
    processTimelinesRecurse(lineGPS, lineHRD, Nil).reverse.map(autodetectSport)
  }
}