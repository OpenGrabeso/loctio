package com.github.opengrabeso.mixtio

case class Vector2(x: Double, y: Double) {
  def + (that: Vector2) = Vector2(this.x + that.x, this.y + that.y)
  def - (that: Vector2) = Vector2(this.x - that.x, this.y - that.y)
  def * (f: Double) = Vector2(this.x * f, this.y * f)
  def dot (that: Vector2) = this.x * that.x + this.y * that.y
  def size = math.sqrt(this dot this)
  def size2 = this dot this

  def dist(that: Vector2) = (this - that).size
  def dist2(that: Vector2) = (this - that).size2
}

object Vector2 {
  def nearestOnSegment(v: Vector2, w: Vector2)(p: Vector2): Vector2 = {
    val line2 = v dist2 w
    if (line2 == 0f) v
    else {
      val t = ((p - v) dot (w - v)) / line2
      if (t < 0) v
      else if (t > 1) w
      else v + (w - v) * t
    }
  }
}