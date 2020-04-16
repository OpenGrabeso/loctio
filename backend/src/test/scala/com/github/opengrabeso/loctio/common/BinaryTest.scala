package com.github.opengrabeso.loctio.common

import Binary._

class BinaryTest extends org.scalatest.funsuite.AnyFunSuite {
  test("Binary address encoding ") {
    assert(fromIpAddress("1.2.3.4") == "00000001000000100000001100000100")
  }

  test("Distance") {
    assert(distance("111","111") == 0)
    assert(distance("111","110") == 1)
    assert(distance("100","111") == 3)
    assert(distance("111","100") == 3)
  }

  test("IP address distance") {
    assert(distance(fromIpAddress("1.0.0.0"), fromIpAddress("1.0.1.0")) == 256)
  }

  test ("Common prefix") {
    assert(commonPrefix(Seq("101011")) == "101011")
    assert(commonPrefix(Seq("101010", "101000")) == "1010")
  }
}
