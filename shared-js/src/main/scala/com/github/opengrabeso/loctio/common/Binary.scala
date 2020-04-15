package com.github.opengrabeso.loctio.common

object Binary {
  // string implementation is easy to get right
  // if performance is an issue, it would be possible to implement the same functionality using Long


  def distance(a: String, b: String) = {
    (a zip b).map {case (x, y) => x != y}.foldLeft(0L) { (dist, diff) =>
      dist * 2 + (if (diff) 1 else 0)
    }
  }

  def commonPrefixLength(s: Seq[String]): Int = {
    @scala.annotation.tailrec
    def recurse(s: Seq[String], common: Int): Int = {
      // if the prefix is the same for all, try a longer prefix if possible
      val prefixes = s.map(_.take(common)).distinct
      if (prefixes.length > 1) common
      else if (s.forall(_.length <= common)) common + 1
      else recurse(s, common + 1)
    }
    recurse(s, 1) - 1 // minus 1 because we receive a first failure
  }

  def commonPrefix(s: Seq[String]): String = {
    s.head.take(commonPrefixLength(s))
  }

  def fromIpAddress(addr: String): String = {
    def binary8bit(i: Int) = {
      for (b <- 0 until 8) yield {
        if ((i & (128 >> b))!= 0) '1'
        else '0'
      }
    }

    val parts = addr.split('.')
    parts.flatMap(p => binary8bit(p.toInt)).mkString
  }

}
