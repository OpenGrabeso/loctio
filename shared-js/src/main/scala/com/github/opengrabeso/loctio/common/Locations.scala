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

    val networkMap = storage.load[Seq[(String,String)]](FullName("network")).toSeq.flatten
    val exactMatch = networkMap.collectFirst { case (networkAddr, name) if networkAddr == ipAddress =>
      name
    }

    exactMatch.getOrElse(ipAddress.trim)
  }

  def networksFromLocations(): Unit = {
    val allLocations = storage.listAllItems().toSeq

    val networkMap = allLocations.flatMap { i =>
      val name = storage.itemName(i)
      if (name.startsWith("locations/")) {
        val locationName = dropNamespace(name)
        storage.load[String](FullName(name)).map(_ -> locationName)
      } else None
    }
    // we prefer storing as a single file, this way we can be sure the storage is atomic and there are no obsolete files left
    storage.store(FullName("network"), networkMap)
  }

  def nameLocation(ipAddress: String, name: String): Unit = {
    // if multiple location names are similar, merge them (assume a dynamic address from an ISP pool)
    // avoid merging ranges too aggresively
    storage.store(FullName("locations", name), ipAddress)
    networksFromLocations()
  }

}

