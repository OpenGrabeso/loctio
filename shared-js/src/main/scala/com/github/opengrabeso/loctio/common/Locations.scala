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
    val binaryIP = Binary.fromIpAddress(ipAddress)
    val matches = networkMap.collect { case (networkAddr, name) if binaryIP.startsWith(networkAddr) =>
      name
    }
    // select the longest match
    val bestMatch = if (matches.nonEmpty) Some(matches.maxBy(_.length)) else None
    bestMatch.orElse {
      // if exact match is not possible, try guessing
      val guesses = networkMap.flatMap { case (networkAddr, name) =>
        val common = Binary.commonPrefixLength(Seq(networkAddr, binaryIP))
        if (common >= 24) Some(common -> (name + "(?)"))
        else if (common >= 16) Some(common -> (name + "(??)"))
        else if (common >= 8) Some(common -> (name + "(???)"))
        else None
      }
      if (guesses.nonEmpty) Some(guesses.maxBy(_._1)._2) else None
    }.getOrElse(ipAddress.trim)
  }

  def networksFromLocations(): Unit = {
    val allLocations = storage.enumerate("locations/").map(_._1)

    // pairs: binary address -> name
    val networkMap = allLocations.flatMap { i =>
      val name = storage.itemName(i)
      if (name.startsWith("locations/")) {
        val addr = dropNamespace(name)
        storage.load[String](FullName(name)).map(location => Binary.fromIpAddress(addr) -> location)
      } else None
    }

    val namedGroups = networkMap.groupBy(_._2).map { case (name, items) =>
      val addresses = items.map(_._1)
      // make groups starting with the same 8 bits
      // in each of the groups determine the necessary common prefix for the left items
      name -> addresses.groupBy(_.take(8)).map { case (prefix, values) =>
        Binary.commonPrefix(values)
      }
    }.toSeq.flatMap { case (name, items) =>
      items.map(_ -> name)
    }

    // we prefer storing as a single file, this way we can be sure the storage is atomic and there are no obsolete files left
    storage.store(FullName("network"), namedGroups)
  }

  def nameLocation(ipAddress: String, name: String): Unit = {
    // if multiple location names are similar, merge them (assume a dynamic address from an ISP pool)
    // avoid merging ranges too aggresively
    storage.store(FullName("locations", ipAddress), name)
    networksFromLocations()
  }

}

