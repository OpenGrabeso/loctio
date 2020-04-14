package com.github.opengrabeso.loctio

import org.scalatest.funsuite._

class MainJS extends AnyFunSuite {
  test("Dummy test") {
    info("Test running OK")
  }
  test("Use shared code") {
    val name = appName
    assert(name.nonEmpty)
    info(s"Shared function running OK on JVM: $name")
  }
}
