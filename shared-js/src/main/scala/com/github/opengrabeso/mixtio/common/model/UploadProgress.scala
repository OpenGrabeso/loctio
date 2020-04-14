package com.github.opengrabeso.mixtio
package common.model

sealed trait UploadProgress

object UploadProgress extends rest.EnhancedRestDataCompanion[UploadProgress] {
  case class Pending(id: String) extends UploadProgress
  object Pending extends rest.EnhancedRestDataCompanion[Pending]
  case class Done(stravId: Long, id: String) extends UploadProgress
  object Done extends rest.EnhancedRestDataCompanion[Done]
  case class Error(id: String, error: String) extends UploadProgress
  object Error extends rest.EnhancedRestDataCompanion[Error]
  case class Duplicate(id: String, stravaId: Long) extends UploadProgress
  object Duplicate extends rest.EnhancedRestDataCompanion[Error]
}
