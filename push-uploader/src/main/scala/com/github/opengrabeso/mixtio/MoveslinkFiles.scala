package com.github.opengrabeso.mixtio
import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.io.File
import java.nio.file.Files
import java.nio.charset.StandardCharsets

import scala.util.Try

object MoveslinkFiles {

  import MoveslinkUtils._

  private val suuntoHome = getSuuntoHome

  private val moveslinkFolder = "Moveslink"
  private val moveslink2Folder = "Moveslink2"

  private val getDataFolder: File = new File(suuntoHome, moveslinkFolder)
  private val getData2Folder: File = new File(suuntoHome, moveslink2Folder)

  private def placeInFolder(folder: String, filename: String): String = folder + "/" + filename

  // no need to sync Moveslink settings files, we want only activities
  private def exclude = Seq("library.xml", "settings.xml")

  private def safeFileSet(file: File) = {
    val ss = Option(file.list).toSet
    ss.flatMap(s => s.toSet)
  }

  def listQuestFiles: Set[String] = safeFileSet(getDataFolder).filter(name => name.endsWith(".xml") && name.toLowerCase.startsWith("quest_"))

  def listMoveslink2Files: Set[String] = safeFileSet(getData2Folder).filter(f => f.endsWith(".sml") || f.endsWith(".xml") && !exclude.contains(f.toLowerCase))

  def listFiles: Set[String] = listQuestFiles.map(placeInFolder(moveslinkFolder, _)) ++ listMoveslink2Files.map(placeInFolder(moveslink2Folder, _))

  def get(path: String): Option[Array[Byte]] = {
    if (listFiles.contains(path)) {
      Try {
        Files.readAllBytes(suuntoHome.toPath.resolve(path))
      }.toOption
    } else None
  }

  def getString(path: String): Option[String] = {
    get(path).map(new String(_, StandardCharsets.UTF_8))
  }

  def timestampFromName(name: String): Option[ZonedDateTime] = {
    // extract timestamp
    // GPS filename: Moveslink2/34FB984612000700-2017-05-23T16_27_11-0.sml
    val gpsPattern = "/.*-(\\d*)-(\\d*)-(\\d*)T(\\d*)_(\\d*)_(\\d*)-".r.unanchored
    // Quest filename Moveslink/Quest_2596420792_20170510143253.xml
    val questPattern = "/Quest_\\d*_(\\d\\d\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)(\\d\\d)\\.".r.unanchored
    // note: may be different timezones, but a rough sort in enough for us (date is important)
    name match {
      case gpsPattern(yyyy,mm,dd,h,m,s) =>
        Some(ZonedDateTime.parse(s"$yyyy-$mm-${dd}T$h:$m:$s+00:00")) // TODO: DRY
      case questPattern(yyyy,mm,dd,h,m,s) =>
        Some(LocalDateTime.parse(s"$yyyy-$mm-${dd}T$h:$m:$s").atZone(ZoneId.systemDefault)) // TODO: DRY
      case _ =>
        None
    }
  }


}
