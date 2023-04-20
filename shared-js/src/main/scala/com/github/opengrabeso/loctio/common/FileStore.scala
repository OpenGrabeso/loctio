package com.github.opengrabeso.loctio
package common

import com.avsystem.commons.serialization.GenCodec

import java.time.temporal.ChronoUnit
import java.time.{ZoneId, ZonedDateTime}
import scala.reflect.ClassTag

object FileStore {

  // full name combined - namespace +  filename
  object FullName {
    def apply(namespace: String, filename: String): FullName = {
      FullName(namespace + "/" + filename)
    }
  }

  case class FullName(name: String) {
    def + (s: String): FullName = FullName(name + s)
  }

}

import FileStore._

trait FileStore {
  type FileItem

  def maxAgeByName(name: String): Option[Int] = {
    val test = false
    val maxSessionAge = if (test) 0 else 90
    val maxAgeInDays = Some(maxSessionAge)
    maxAgeInDays
  }

  def itemModified(fileItem: FileItem): Option[java.util.Date]

  def enumerate(prefix: String): Seq[(FileItem, FullName, String)]

  def deleteItem(item: FileItem): Unit

  def store[T: GenCodec](name: FullName, obj: T, metadata: (String, String)*): Unit
  def load[T: ClassTag: GenCodec](name: FullName): Option[T]

  def itemName(item: FileItem): String

  def cleanup(): Int = {
    val list = enumerate("").map(_._1)
    val now = ZonedDateTime.now(ZoneId.of("Z"))

    val ops = for (i <- list) yield {
      val name = itemName(i)
      // cleanup requirements different for different namespaces
      val maxAgeInDays = maxAgeByName(name)

      val cleaned = for {
        maxDays <- maxAgeInDays
        modTime <- itemModified(i)
      } yield {
        val fileTime = ZonedDateTime.ofInstant(modTime.toInstant, ZoneId.of("Z"))
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
