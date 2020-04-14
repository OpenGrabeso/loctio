package com.github.opengrabeso.mixtio
package common.model

import rest.EnhancedRestDataCompanion

@SerialVersionUID(10L)
sealed trait FileId {
  def filename: String
  def stravaId: String
  def toReadableString: String = toString
}

object FileId extends EnhancedRestDataCompanion[FileId] {
  def parse(actId: String): FileId = {
    if (actId.last == ')') {
      val prefix = actId.takeWhile(_ != '(')
      val content = actId.drop(prefix.length + 1).dropRight(1)

      prefix match {
        case "FilenameId" =>
          FilenameId(content)
        case "StravaId" =>
          StravaId(content.toLong)
        case "TempId" =>
          TempId(content)
      }
    } else {
      throw new UnsupportedOperationException(s"Malformed activity id '$actId")
    }
  }

  @SerialVersionUID(10L)
  case class StravaId(id: Long) extends FileId {
    def filename = "events-" + id.toString
    def stravaId = id.toString
  }

  // while uploading we assign each upload a temporary upload id
  @SerialVersionUID(10L)
  case class StravaUploadingId(id: String) extends FileId {
    def filename = "uploading:" + id
    def stravaId = ""
    override def toReadableString: String = "Uploading..."
  }
  @SerialVersionUID(10L)
  case class FilenameId(id: String) extends FileId {
    def filename = id
    def stravaId = ""
    override def toReadableString: String = "File " + id  // TODO: check Quest / GPS filename?
  }

  @SerialVersionUID(10L)
  case class TempId(id: String) extends FileId {
    final private val prefix = "temp-"
    def filename = if (id.startsWith(prefix)) id else "temp-" + id
    def stravaId = ""
  }

  @SerialVersionUID(10L)
  case object NoId extends FileId {
    def filename = ""
    def stravaId = ""
  }
}
