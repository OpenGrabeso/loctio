package com.github.opengrabeso.loctio.common

import FileStore._

object Locations {
    def dropNamespace(s: String) = s.dropWhile(_ != '/').drop(1)
}

import Locations._

class Locations(storage: FileStore) {
  def locationFromIpAddress(ipAddress: String): String = {
    val allLocations = storage.listAllItems().toSeq
    val exactMatch = allLocations.flatMap { i =>
      val name = storage.itemName(i)
      storage.load[String](FullName(name)).filter(_ == ipAddress).map(dropNamespace(name) -> _)
    }.headOption

    exactMatch.map(_._1).getOrElse(ipAddress.trim)
  }

  def nameLocation(ipAddress: String, name: String): Unit = {
    storage.store(FullName("locations", name), ipAddress)
  }

}

