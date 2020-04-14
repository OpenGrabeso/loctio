package com.github.opengrabeso.mixtio
package common.model

@SerialVersionUID(12L)
// median, 80% percentile, max
case class SpeedStats(median: Double, fast: Double, max: Double)

object SpeedStats extends rest.EnhancedRestDataCompanion[SpeedStats]
