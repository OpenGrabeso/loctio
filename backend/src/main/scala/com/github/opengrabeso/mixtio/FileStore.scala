package com.github.opengrabeso.mixtio

import java.time.temporal.ChronoUnit

import Main._
import java.time.{ZoneId, ZonedDateTime}

trait FileStore {
  type FileItem

  def maxAgeByName(name: String): Option[Int] = {
    val test = false
    val maxSessionAge = if (test) 0 else 2
    val maxAgeInDays = if (name.contains("/" + namespace.stage + "/")) {
      Some(90)
    } else if (name.contains("/" + namespace.settings + "/")) {
      Some(365)
    } else if (name.contains("/" + namespace.edit + "/")) {
      Some(14)
    } else if (name.contains("/" + namespace.uploadProgress + "/")) { // must be listed before namespace.upload, that would match more eagerly
      Some(maxSessionAge)
    } else if (name.contains("/" + namespace.uploadResult(""))) {
      Some(maxSessionAge)
    } else if (name.contains("/" + namespace.upload(""))) {
      Some(maxSessionAge)
    } else {
      None
    }
    maxAgeInDays
  }

  def itemModified(fileItem: FileItem): Option[java.util.Date]

  def listAllItems(): Iterable[FileItem]

  def deleteItem(item: FileItem): Unit

  def itemName(item: FileItem): String

  def cleanup(): Int = {
    val list = listAllItems()
    val now = ZonedDateTime.now()

    val ops = for (i <- list) yield {
      val name = itemName(i)
      // cleanup requirements different for different namespaces
      val maxAgeInDays = maxAgeByName(name)

      val cleaned = for {
        maxDays <- maxAgeInDays
        modTime <- itemModified(i)
      } yield {
        val fileTime = ZonedDateTime.ofInstant(modTime.toInstant, ZoneId.systemDefault)
        val age = ChronoUnit.DAYS.between(fileTime, now)
        if (age >= maxDays) {
          deleteItem(i)
          println(s"Cleaned $name age $age, date $fileTime")
          1
        } else {
          0
        }
      }

      cleaned.getOrElse(0)
    }
    ops.sum

  }

}
