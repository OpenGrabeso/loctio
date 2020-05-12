package com.github.opengrabeso.loctio
package rest

import com.github.opengrabeso.github.rest.ZonedDateTimeCodecs
import com.avsystem.commons.meta.MacroInstances
import com.avsystem.commons.serialization.{GenCodec, HasGenCodecWithDeps}
import io.udash.rest._
import io.udash.rest.openapi.{RestSchema, RestStructure}

trait EnhancedRestImplicits extends DefaultRestImplicits with ZonedDateTimeCodecs

object EnhancedRestImplicits extends EnhancedRestImplicits

abstract class EnhancedRestDataCompanion[T](
  implicit macroCodec: MacroInstances[EnhancedRestImplicits.type, () => GenCodec[T]]
) extends HasGenCodecWithDeps[EnhancedRestImplicits.type, T] {
  implicit val instances: MacroInstances[DefaultRestImplicits, CodecWithStructure[T]] = implicitly[MacroInstances[DefaultRestImplicits, CodecWithStructure[T]]]
  implicit lazy val restStructure: RestStructure[T] = instances(DefaultRestImplicits, this).structure
  implicit lazy val restSchema: RestSchema[T] = RestSchema.lazySchema(restStructure.standaloneSchema)


}


