package com.github.opengrabeso.mixtio
package requests

import Main._
import common.Util._
import spark.{Request, Response}

object Process extends ActivityStorage {

  private def follows(first: ActivityEvents, second: ActivityEvents) = {
    val secondGap = second.secondsInActivity(first.endTime)
    // if second starts no more than 10 minutes after the first ends, merge them
    secondGap < 10 && secondGap > -10 * 60
  }

  private def mergeConsecutive(events: List[ActivityEvents]): List[ActivityEvents] = {
    @scala.annotation.tailrec
    def mergeConsecutiveRecurse(todo: List[ActivityEvents], done: List[ActivityEvents]): List[ActivityEvents] = {
      todo match {
        case first :: second :: tail if first.id.sportName == second.id.sportName && follows(first, second) =>
          mergeConsecutiveRecurse(tail, first.merge(second) :: done)
        case first :: tail =>
          mergeConsecutiveRecurse(tail, first :: done)
        case Nil =>
          done
      }
    }
    mergeConsecutiveRecurse(events, Nil).reverse
  }

  def mergeForUpload(auth: Main.StravaAuthResult, toMerge: Seq[ActivityEvents]): Seq[ActivityEvents] = {
    if (toMerge.nonEmpty) {

      val (gpsMoves, attrMovesRaw) = toMerge.partition(_.hasGPS)

      val timeOffset = Settings(auth.userId).questTimeOffset
      val ignoreDuration = 30

      val attrMoves = attrMovesRaw.map(_.timeOffset(-timeOffset))

      def filterIgnored(x: ActivityEvents) = x.isAlmostEmpty(ignoreDuration)

      val timelineGPS = gpsMoves.toList.filterNot(filterIgnored).sortBy(_.startTime)
      val timelineAttr = mergeConsecutive(attrMoves.toList.filterNot(filterIgnored).sortBy(_.startTime))

      moveslink.MovesLinkUploader.processTimelines(timelineGPS, timelineAttr)

    } else Nil
  }
  def mergeAndUpload(auth: Main.StravaAuthResult, toMerge: Seq[ActivityEvents], sessionId: String): Seq[String] = {
    val merged = mergeForUpload(auth, toMerge)
    uploadMultiple(merged)(auth, sessionId)
  }
}