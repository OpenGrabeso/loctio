package com.github.opengrabeso.mixtio.mapbox

object TileBelt {
  /* ScalaFromJS: 2017-10-23 09:41:46.284*/
  // from NPM @mapbox / tilebelt

  val d2r = Math.PI / 180
  val r2d = 180 / Math.PI

  def tileToBBOX(tile: Array[Int]): Array[Double] = {
    val e = tile2lon(tile(0) + 1, tile(2))
    val w = tile2lon(tile(0), tile(2))
    val s = tile2lat(tile(1) + 1, tile(2))
    val n = tile2lat(tile(1), tile(2))
    Array(w, s, e, n)
  }

  case class GeoJSONPoly(coordinates: Array[Array[Array[Double]]])

  def tileToGeoJSON(tile: Array[Int]) = {
    val bbox = tileToBBOX(tile)
    GeoJSONPoly(Array(Array(Array(bbox(0), bbox(1)), Array(bbox(0), bbox(3)), Array(bbox(2), bbox(3)), Array(bbox(2), bbox(1)), Array(bbox(0), bbox(1)))))
  }

  def tile2lon(x: Double, z: Double) = {
    x / Math.pow(2, z) * 360 - 180
  }

  def tile2lat(y: Double, z: Double) = {
    val n = Math.PI - 2 * Math.PI * y / Math.pow(2, z)
    r2d * Math.atan(0.5 * (Math.exp(n) - Math.exp(-n)))
  }

  def pointToTile(lon: Double, lat: Double, z: Int): Array[Long] = {
    val tile = pointToTileFraction(lon, lat, z)
    Array(
      Math.floor(tile(0)).toLong,
      Math.floor(tile(1)).toLong,
      z
    )
  }

  def getChildren(tile: Array[Int]): Array[Array[Int]] = {
    Array(Array(tile(0) * 2, tile(1) * 2, tile(2) + 1), Array(tile(0) * 2 + 1, tile(1) * 2, tile(2) + 1), Array(tile(0) * 2 + 1, tile(1) * 2 + 1, tile(2) + 1), Array(tile(0) * 2, tile(1) * 2 + 1, tile(2) + 1))
  }

  def getParent(tile: Array[Int]): Array[Int] = {
    // top left
    if ((tile(0) % 2 == 0) && (tile(1) % 2 == 0)) {
      return Array(tile(0) / 2, tile(1) / 2, tile(2) - 1)
    }
    // bottom left
    if ((tile(0) % 2 == 0) && !(tile(1) % 2 == 0)) {
      return Array(tile(0) / 2, (tile(1) - 1) / 2, tile(2) - 1)
    }
    // top right
    if (!(tile(0) % 2 == 0) && (tile(1) % 2 == 0)) {
      return Array((tile(0) - 1) / 2, tile(1) / 2, tile(2) - 1)
    }
    // bottom right
    Array((tile(0) - 1) / 2, (tile(1) - 1) / 2, tile(2) - 1)
  }

  def getSiblings(tile: Array[Int]) = {
    getChildren(getParent(tile))
  }

  def hasSiblings(tile: Array[Int], tiles: Array[Array[Int]]): Boolean = {
    val siblings = getSiblings(tile)
    for (i <- siblings.indices) {
      if (!hasTile(tiles, siblings(i))) return false
    }
    true
  }

  def hasTile(tiles: Array[Array[Int]], tile: Array[Int]): Boolean = {
    for (i <- tiles.indices) {
      if (tilesEqual(tiles(i), tile)) return true
    }
    false
  }

  def tilesEqual(tile1: Array[Int], tile2: Array[Int]) = {
    tile1(0) == tile2(0) && tile1(1) == tile2(1) && tile1(2) == tile2(2)
  }

  def tileToQuadkey(tile: Array[Long]) = {
    var index = ""
    for (z <- tile(2) until 0 by -1) {
      var b = 0
      val mask = 1L << (z - 1)
      if ((tile(0) & mask) != 0) b += 1
      if ((tile(1) & mask) != 0) b += 2
      index += b.toString
    }
    index
  }
  def quadkeyToTile(quadkey: String): Array[Int] = {
    var x = 0
    var y = 0
    val z = quadkey.length
    for (i <- z until 0 by -1) {
      val mask = 1 << (i - 1)
      val q = quadkey(z - i).asDigit
      if (q == 1) x |= mask
      if (q == 2) y |= mask
      if (q == 3) {
        x |= mask
        y |= mask
      }
    }
    Array(x, y, z)
  }

  def bboxToTile(bboxCoords: Array[Double]): Array[Long] = {
    val min = pointToTile(bboxCoords(0), bboxCoords(1), 32)
    val max = pointToTile(bboxCoords(2), bboxCoords(3), 32)
    val bbox = Array(min(0), min(1), max(0), max(1))
    val z = getBboxZoom(bbox)
    if (z == 0) return Array(0, 0, 0)
    val x = (bbox(0) >> (32 - z)) & Int.MaxValue
    val y = (bbox(1) >> (32 - z)) & Int.MaxValue
    Array(x, y, z)
  }

  def getBboxZoom(bbox: Array[Long]): Int = {
    val MAX_ZOOM = 28
    for (z <- 0 until MAX_ZOOM) {
      val mask = 1L << (32 - (z + 1))
      if ((bbox(0) & mask) != (bbox(2) & mask) || (bbox(1) & mask) != (bbox(3) & mask)) {
        return z
      }
    }
    MAX_ZOOM
  }

  def pointToTileFraction(lon: Double, lat: Double, z: Int): Array[Double] = {
    val sin = Math.sin(lat * d2r)
    val z2 = Math.pow(2, z)
    val x = z2 * (lon / 360 + 0.5)
    val y = z2 * (0.5 - 0.25 * Math.log((1 + sin) / (1 - sin)) / Math.PI)
    Array(x, y, z)
  }

}
