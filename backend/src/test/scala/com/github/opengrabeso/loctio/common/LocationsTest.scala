package com.github.opengrabeso.loctio.common

class LocationsTest extends org.scalatest.funsuite.AnyFunSuite {
  test("Add and retrieve") {
    object locations extends Locations(new TestStorage)

    locations.nameLocation("127.0.0.0", "localhost")

    assert(locations.locationFromIpAddress("127.0.0.0") == "localhost")
  }

  test("Unknown address") {
    object locations extends Locations(new TestStorage)

    locations.nameLocation("127.0.0.0", "localhost")

    assert(locations.locationFromIpAddress("192.168.0.1") == "192.168.0.1")
  }

  def testForMany(sourceData: Seq[(String, String)], testData: Seq[(String, String)]) = {
    object locations extends Locations(new TestStorage)

    sourceData.foreach(an => locations.nameLocation(an._1, an._2))

    sourceData.foreach { an =>
      assert(locations.locationFromIpAddress(an._1) == an._2)
    }
    testData.foreach { an =>
      assert(locations.locationFromIpAddress(an._1) == an._2)
    }

  }
  test("Merge IP address ranges") {
    val sourceData = Seq(
      "55.66.71.10" -> "HomeB",
      "55.66.71.7" -> "HomeB",
      "55.66.71.9" -> "HomeB",
      "88.77.11.134" -> "Office",
      "88.77.11.146" -> "Office",
      "88.77.11.153" -> "Office",
      "88.77.11.187" -> "Office",
      "44.33.130.205" -> "Office",
      "44.33.131.106" -> "Office",
      "44.33.131.113" -> "Office",
      "44.33.131.232" -> "Office",
      "44.33.132.214" -> "Office",
      "44.33.134.246" -> "Office",
      "44.33.135.15" -> "Office",
      "44.33.165.229" -> "Office",
      "44.33.165.70" -> "Office",
      "44.33.167.200" -> "Office",
      "44.33.167.27" -> "Office",
      "44.33.168.56" -> "Office",
      "44.33.169.107" -> "Office",
      "44.33.169.160" -> "Office",
      "44.33.169.181" -> "Office",
      "44.33.225.243" -> "Office",
      "22.121.157.90" -> "HomeA",
      "22.122.168.41" -> "HomeA"
    )

    val testData = Seq(
      "55.66.71.8" -> "HomeB",
      "88.77.11.160" -> "Office",
      "88.77.11.169" -> "Office",
      "44.33.135.179" -> "Office",
      "44.33.165.150" -> "Office",
      "44.33.169.172" -> "Office",
      "44.33.225.25" -> "Office",
      "44.33.226.176" -> "Office",
      "22.121.213.139" -> "HomeA",
    )

    testForMany(sourceData, testData)
  }



  test("Select the more specific match") {
    val sourceData = Seq(
      "44.33.131.205" -> "Office",
      "44.33.131.106" -> "Office",
      "44.33.131.113" -> "Office",
      "44.33.131.232" -> "Office",
      "44.33.131.213" -> "Special",
      "44.33.131.217" -> "Special",
    )
    val testData = Seq(
      "44.33.131.110" -> "Office",
      "44.33.131.215" -> "Special",
    )

    testForMany(sourceData, testData)

  }

  test("Guess when there is no exact match") {
    val sourceData = Seq(
      "44.33.131.205" -> "Office",
    )
    val testData = Seq(
      "44.33.131.110" -> "Office(?)",
      "44.33.121.215" -> "Office(??)",
      "44.22.121.215" -> "Office(???)",
      "1.2.3.4" -> "1.2.3.4",
    )

    testForMany(sourceData, testData)

  }
}
