package com.github.opengrabeso.mixtio
package frontend
package views

import common.model._
import org.scalajs.dom.html
import scalatags.JsDom.all._
import org.scalajs.dom.raw.HTMLElement

import scala.scalajs.js
import scala.util.Try

trait ActivityLink {
  def niceFileName(fileId: String): Option[html.Element] = {
    object IsInt {
      def unapply(x: String): Option[Int] = Try(x.toInt).toOption
    }
    // TODO: DRY with com.github.opengrabeso.mixtio.MoveslinkFiles.timestampFromName
    // GPS filename: Moveslink2/34FB984612000700-2017-05-23T16_27_11-0.sml
    val gpsPattern = "(.*)/.*-(\\d*)-(\\d*)-(\\d*)T(\\d*)_(\\d*)_(\\d*)-".r.unanchored
    // Quest filename Moveslink/Quest_2596420792_20170510143253.xml
    val questPattern = "(.*)/Quest_\\d*_(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)\\.".r.unanchored
    // note: may be different timezones, but a rough sort in enough for us (date is important)
    val specialCase = fileId match {
      case gpsPattern(folder, IsInt(yyyy), IsInt(mm), IsInt(dd), IsInt(h), IsInt(m), IsInt(s)) =>
        Some(folder, new js.Date(yyyy, mm - 1, dd, h, m, s)) // filename is a local time of the activity beginning
      case questPattern(folder, IsInt(yyyy), IsInt(mm), IsInt(dd), IsInt(h), IsInt(m), IsInt(s)) =>
        Some(folder, new js.Date(yyyy, mm - 1, dd, h, m, s)) // filename is a local time when the activity was downloaded
      case _ =>
        None
    }
    specialCase.map { case (folder, date) =>
      import TimeFormatting._
      span(
        title := fileId,
        b(folder),
        ": ",
        formatDateTime(date)
      ).render
    }

  }

  def hrefLink(ai: FileId, shortName: => String): HTMLElement = {
    ai match {
      case FileId.StravaId(num) =>
        a(
          // TODO: CSS color "#FC4C02"
          href := s"https://www.strava.com/activities/$num",
          shortName
        ).render
      case FileId.FilenameId(fileId) =>
        niceFileName(fileId).getOrElse(div(ai.toReadableString).render)
      case _ =>
        div(ai.toReadableString).render
    }
  }

}

object ActivityLink extends ActivityLink