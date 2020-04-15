package com.github.opengrabeso.loctio.common

class LocationsTest extends org.scalatest.FunSuite {
  test("Add and retrieve") {
    object locations extends Locations(TestStorage)

    locations.nameLocation("127.0.0.0", "localhost")

    assert(locations.locationFromIpAddress("127.0.0.0") == "localhost")
  }
}
