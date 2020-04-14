package com.github.opengrabeso.mixtio

import org.scalatest.funsuite.AnyFunSuite

class SharedCode extends AnyFunSuite {
  test("Use shared code") {
    val name = appName
    assert(name.nonEmpty)
    info(s"Shared function running OK on JS: $name")

  }
}
