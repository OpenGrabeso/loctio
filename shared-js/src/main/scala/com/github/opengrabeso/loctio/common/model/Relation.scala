package com.github.opengrabeso.loctio
package common.model

import com.avsystem.commons.serialization.GenCodec
import io.udash.properties.ModelPropertyCreator
import rest.EnhancedRestDataCompanion

// possible values for watching / watched by
sealed trait Relation

object Relation extends EnhancedRestDataCompanion[Relation] {

  case object No extends Relation
  case object Requested extends Relation
  case object Yes extends Relation
}
