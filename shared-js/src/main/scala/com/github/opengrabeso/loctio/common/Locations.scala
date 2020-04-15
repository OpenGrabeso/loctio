package com.github.opengrabeso.loctio.common

import FileStore._

object Locations {
  def dropNamespace(s: String) = s.dropWhile(_ != '/').drop(1)

}

import Locations._

class Locations(storage: FileStore) {
  // "locations" contains source naming data, as given by users
  // "networks" contains mapping from IP address to name

  def locationFromIpAddress(ipAddress: String): String = {
    // TODO: consider some preliminary filtering in listAllItems to avoid listing all addresses

    val allLocations = storage.listAllItems().toSeq
    val exactMatch = allLocations.flatMap { i =>
      val name = storage.itemName(i)
      if (name.startsWith("networks/")) {
        val networkAddr = dropNamespace(name)
        if (networkAddr == ipAddress) {
          storage.load[String](FullName(name))
        } else {
          None
        }
      } else {
        None
      }
    }.headOption

    exactMatch.getOrElse(ipAddress.trim)
  }

  def networksFromLocations(): Unit = {
    val allLocations = storage.listAllItems().toSeq
    allLocations.foreach { i =>
      val name = storage.itemName(i)
      if (name.startsWith("locations/")) {
        val locationName = dropNamespace(name)
        for (ipAddress <- storage.load[String](FullName(name))) {
          storage.store(FullName("networks", ipAddress), locationName)
        }
      }
    }
  }

  def nameLocation(ipAddress: String, name: String): Unit = {
    // if multiple location names are similar, merge them (assume a dynamic address from an ISP pool)
    // avoid merging ranges too aggresively
    storage.store(FullName("locations", name), ipAddress)
    networksFromLocations()
  }

}

