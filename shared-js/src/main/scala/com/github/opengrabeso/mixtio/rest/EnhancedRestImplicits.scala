package com.github.opengrabeso.mixtio
package rest

import java.time.ZonedDateTime

import com.avsystem.commons.meta.MacroInstances
import com.avsystem.commons.serialization.{GenCodec, GenKeyCodec, HasGenCodecWithDeps}
import com.github.opengrabeso.mixtio.common.model.SportId
import io.udash.rest._
import io.udash.rest.openapi.{RestSchema, RestStructure}

trait EnhancedRestImplicits extends DefaultRestImplicits {
  import EnhancedRestImplicits._

  implicit val zonedDateTimeCodec: GenCodec[ZonedDateTime] = GenCodec.fromApplyUnapplyProvider(ZonedDateTimeAU)
  implicit val zonedDateTimeKeyCodec: GenKeyCodec[ZonedDateTime] = GenKeyCodec.create(ZonedDateTime.parse,_.toString)

  implicit val sportIdTimeCodec: GenCodec[SportId.SportId] = GenCodec.fromApplyUnapplyProvider(SportIdAU)
  implicit val sportIdTimeKeyCodec: GenKeyCodec[SportId.SportId] = GenKeyCodec.create(SportId.withName,_.toString)
}

object EnhancedRestImplicits extends EnhancedRestImplicits {
  object ZonedDateTimeAU {
    def apply(string: String): ZonedDateTime = ZonedDateTime.parse(string)
    def unapply(dateTime: ZonedDateTime): Option[String] = Some(dateTime.toString)
  }

  object SportIdAU {
    def apply(name: String): SportId.Value = SportId.withName(name)
    def unapply(value: SportId.SportId): Option[String] = Some(value.toString)
  }
}

abstract class EnhancedRestDataCompanion[T](
  implicit macroCodec: MacroInstances[EnhancedRestImplicits.type, () => GenCodec[T]]
) extends HasGenCodecWithDeps[EnhancedRestImplicits.type, T] {
  implicit val instances: MacroInstances[DefaultRestImplicits, CodecWithStructure[T]] = implicitly[MacroInstances[DefaultRestImplicits, CodecWithStructure[T]]]
  implicit lazy val restStructure: RestStructure[T] = instances(DefaultRestImplicits, this).structure
  implicit lazy val restSchema: RestSchema[T] = RestSchema.lazySchema(restStructure.standaloneSchema)
}


