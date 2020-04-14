package com.github.opengrabeso.mixtio
package common.model

@SerialVersionUID(11L)
case class ActivityHeader(id: ActivityId, hasGPS: Boolean, hasAttributes: Boolean, stats: SpeedStats) {
  override def toString = id.toString
  def describeData = (hasGPS, hasAttributes) match {
    case (true, true) => "GPS+"
    case (true, false) => "GPS"
    case (false, true) => "+"
    case (false, false) => "--"
  }
}

object ActivityHeader extends rest.EnhancedRestDataCompanion[ActivityHeader]