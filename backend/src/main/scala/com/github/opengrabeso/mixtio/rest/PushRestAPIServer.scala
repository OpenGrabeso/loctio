package com.github.opengrabeso.mixtio
package rest

import java.io.{ByteArrayInputStream, InputStream, PushbackInputStream}
import java.util.zip.GZIPInputStream

import PushRestAPIServer._

object PushRestAPIServer {
  private def decompressStream(input: InputStream): InputStream = {
    val pushbackInputStream = new PushbackInputStream(input, 2)
    val signature = new Array[Byte](2)
    pushbackInputStream.read(signature)
    pushbackInputStream.unread(signature)
    if (signature(0) == 0x1f.toByte && signature(1) == 0x8b.toByte) new GZIPInputStream(pushbackInputStream)
    else pushbackInputStream
  }

  // we want the file to exist, but we do not care for the content (and we are never reading it)
  @SerialVersionUID(10L)
  case object Anything

  @SerialVersionUID(10L)
  case class TextFile(text: String)
}

class PushRestAPIServer(parent: UserRestAPIServer, session: String, localTimeZone: String) extends PushRestAPI with RestAPIUtils {
  private final val markerFileName = "/started"

  private def userId: String = parent.userAuth.id

  def offerFiles(files: Seq[(String, String)]) = syncResponse {
    // check which files do not match a digest
    val needed = files.filterNot { case (file, digest) =>
      Storage.check(Main.namespace.stage, userId, file, digest)
    }.map(_._1)

    val pushNamespace = Main.namespace.pushProgress(session)
    for (f <- needed) {
      // TODO: store some file information (size, ...)
      Storage.store(pushNamespace, f, userId, Anything, Anything, metadata = Seq("status" -> "offered"))
    }
    // write started tag once the pending files information is complete, to mark it can be scanned now
    Storage.store(pushNamespace, markerFileName, userId, TextFile(""), TextFile(""))
    println(s"offered $needed for $userId")
    needed
  }

  // upload a single file
  def uploadFile(id: String, content: Array[Byte], digest: String) = syncResponse {
    // once stored, remove from the "needed" list
    val stream = new ByteArrayInputStream(content)
    val decompressed = decompressStream(stream)

    if (true) { // disable for debugging
      requests.Upload.storeFromStreamWithDigest(userId, id, localTimeZone, decompressed, digest)
    }
    val pushNamespace = Main.namespace.pushProgress(session)
    Storage.updateMetadata(Storage.FullName(pushNamespace, id, userId).name, metadata = Seq("status" -> "pushed"))
    println(s"pushed $id for $userId")
  }

  def reportError(error: String) = syncResponse {
    val pushNamespace = Main.namespace.pushProgress(session)
    // write the marker, write an error into it
    println(s"Reported error $error")
    Storage.store(pushNamespace, markerFileName, userId, TextFile(error), TextFile(error))
  }

  def expected = syncResponse {
    val pushNamespace = Main.namespace.pushProgress(session)
    val sessionPushProgress = Storage.enumerate(pushNamespace, userId)
    val started = sessionPushProgress.exists(_._2 == markerFileName)
    if (!started) {
      (Seq(""), Nil, None) // special response - not empty, but not the list of the files yes
    } else {
      val pendingOrDone = for {
        (_, f) <- sessionPushProgress
        if f != markerFileName
      } yield {
        // check metadata, if done, we can delete the file once reported
        if (Storage.metadataValue(pushNamespace, userId, f, "status").contains("pushed")) {
          Storage.delete(Storage.FullName(pushNamespace, f, userId))
          f -> true
        } else {
          f -> false
        }
      }
      val (pending, done) = pendingOrDone.toSeq.partition(_._2)
      if (pending.isEmpty) {
        val markerContent = Storage.load[TextFile](Storage.FullName(pushNamespace, markerFileName, userId))
        // once we return empty response, we can delete the "started" marker file
        println("push finished")
        for (error <- markerContent) {
          println(s"  Error ${error.text}")
        }
        Storage.delete(Storage.FullName(pushNamespace, markerFileName, userId))
        (pending.map(_._1), done.map(_._1), markerContent.map(_.text))
      } else {
        (pending.map(_._1), done.map(_._1), None)
      }
    }
  }


}
