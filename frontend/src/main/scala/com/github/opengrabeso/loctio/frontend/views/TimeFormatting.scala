package com.github.opengrabeso.loctio.frontend.views

import java.time.{ZoneOffset, ZonedDateTime}

import scala.scalajs.js
import org.scalajs.dom.intl
import org.scalajs.dom.intl.DateTimeFormatOptions

import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.{Date, |}
import TimeFormatting._
trait TimeFormatting {
  def locale: String = {
    import org.scalajs.dom
    val firstLanguage = dom.window.navigator.asInstanceOf[js.Dynamic].languages.asInstanceOf[js.Array[String]].headOption
    firstLanguage.getOrElse(dom.window.navigator.language)
  }

  def timezone: String = {
    new DateTimeFormatX().resolvedOptions().timeZone.getOrElse("Etc/GMT")
  }


  private def formatWithOptions(t: Date, options: DateTimeFormatOptions) = {
    try {
      new intl.DateTimeFormat(
        locale,
        options = options
      ).format(t)
    } catch {
      case _: Exception =>
        s"Invalid time"
    }
  }

  def formatDateTime(t: js.Date): String = {
    formatWithOptions(t, new intl.DateTimeFormatOptions {
      year = "numeric"
      month = "numeric"
      day = "numeric"
      hour = "numeric"
      minute = "numeric"
    })
  }

  def formatDate(t: js.Date): String = {
    formatWithOptions(t, new intl.DateTimeFormatOptions {
      year = "numeric"
      month = "numeric"
      day = "numeric"
    })
  }

  def formatDayOfWeek(t: js.Date): String = {
    formatWithOptions(t, new intl.DateTimeFormatOptions {
      weekday = "long"
    })
  }

  def formatTime(t: js.Date): String = {
    formatWithOptions(t, new intl.DateTimeFormatOptions {
      hour = "numeric"
      minute = "numeric"
    })
  }

  def formatTimeHMS(t: js.Date): String = {
    formatWithOptions(t, new intl.DateTimeFormatOptions {
      hour = "numeric"
      minute = "numeric"
      second = "numeric"
    })
  }

  implicit class ZonedDateTimeOps(t: ZonedDateTime) {
    def toJSDate: js.Date = {
      // without "withZoneSameInstant" the resulting time contained strange [SYSTEM] zone suffix
      val text = t.withZoneSameInstant(ZoneOffset.UTC).toString // (DateTimeFormatter.ISO_ZONED_DATE_TIME)
      new js.Date(js.Date.parse(text))
    }
  }

  def formatDate(t: ZonedDateTime): String = formatDate(t.toJSDate)
  def formatTime(t: ZonedDateTime): String = formatTime(t.toJSDate)
  def formatDateTime(t: ZonedDateTime): String = formatDateTime(t.toJSDate)
  def formatTimeHMS(t: ZonedDateTime): String = formatTimeHMS(t.toJSDate)
  def formatDayOfWeek(t: ZonedDateTime): String = formatDayOfWeek(t.toJSDate)

  def displayTimeRange(startTime: ZonedDateTime, endTime: ZonedDateTime): String = {
    s"${formatDateTime(startTime.toJSDate)}...${formatTime(endTime.toJSDate)}"
  }
}

object TimeFormatting extends TimeFormatting {
  // workaround for https://github.com/scala-js/scala-js-dom/issues/384
  @js.native
  @JSGlobal("Intl.DateTimeFormat")
  class DateTimeFormatX(locales: js.UndefOr[String | js.Array[String]] = js.undefined,
    options: js.UndefOr[DateTimeFormatOptions] = js.undefined)
    extends js.Object {
    def format(date: js.Date): String = js.native
    def resolvedOptions(): DateTimeFormatOptions = js.native
    def supportedLocalesOf(locales: String | js.Array[String],
      options: js.Any): js.Array[String] = js.native
  }

}
