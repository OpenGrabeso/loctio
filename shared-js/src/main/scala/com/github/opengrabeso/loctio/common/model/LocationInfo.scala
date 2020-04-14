package com.github.opengrabeso.loctio
package common.model

import java.time.ZonedDateTime

import rest.EnhancedRestDataCompanion

case class LocationInfo(location: String, lastSeen: String)

object LocationInfo extends EnhancedRestDataCompanion[LocationInfo]

