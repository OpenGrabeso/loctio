package com.github.opengrabeso.mixtio.mapbox

import org.scalactic.TolerantNumerics
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class GetElevationTest extends AnyFunSuite {

  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.1)

  test("test Mount Everest") {
    import GetElevation.DefaultThreadFactory._
    val summit = GetElevation(86.925313, 27.988730)
    assert(summit >= 8742.0)
    assert(summit <= 8743.0)

    val summitRangeF = new GetElevation.TileCache().possibleRange(86.925313, 27.988730)
    val summitRange = Await.result(summitRangeF, Duration.Inf)

    assert(summitRange._1 <= 8742.7)
    assert(summitRange._2 >= 8742.8)

  }

}
