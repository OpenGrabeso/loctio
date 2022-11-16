package com.github.opengrabeso.loctio

import com.avsystem.commons.serialization.GenCodec
import com.avsystem.commons.serialization.json.{JsonOptions, JsonStringInput, JsonStringOutput}
import com.github.opengrabeso.loctio.Presence.UserList

import java.io._
import java.nio.channels.Channels
import com.google.auth.oauth2.GoogleCredentials

import collection.JavaConverters._
import com.google.cloud.storage.{Option => GCSOption, _}
import com.google.cloud.storage.Storage._
import org.apache.commons.io.IOUtils

import scala.reflect.{ClassTag, classTag}
import common.FileStore.FullName

import java.nio.charset.StandardCharsets

object Storage extends common.FileStore {
  final val bucket = "loctio.gamatron.net"

  type FileItem = Blob

  private def fileId(filename: String) = BlobId.of(bucket, filename)

  val credentials = GoogleCredentials.getApplicationDefault
  val storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService

  def output(filename: FullName, metadata: Seq[(String, String)], contentType: String = "application/octet-stream"): OutputStream = {
    val fid = fileId(filename.name)
    val instance = BlobInfo.newBuilder(fid)
      .setMetadata(metadata.toMap.asJava)
      .setContentType(contentType).build()
    val channel = storage.writer(instance)
    Channels.newOutputStream(channel)
  }

  def store[T: GenCodec](name: FullName, obj: T, metadata: (String, String)*): Unit = {
    //println(s"store '$name'")
    val os = output(name + ".json", metadata, contentType = "application/json")
    try {
      val json = JsonStringOutput.write(obj, JsonOptions.Pretty)
      os.write(json.getBytes(StandardCharsets.UTF_8))
    } catch {
      case ex: Exception =>
        println(s"Error in store: ${name.name}: $ex")
        ex.printStackTrace()
        throw ex
    } finally {
      os.close()
    }
  }

  override def load[T : ClassTag: GenCodec](fullName: FullName): Option[T] = {
    try {
      val bytes = storage.readAllBytes(bucket, fullName.name + ".json")
      val s = new String(bytes, StandardCharsets.UTF_8)
      Some(JsonStringInput.read[T](s))
    } catch {
      case ex: StorageException if ex.getCode == 404 =>
        // we expect StorageException - file not found
        None
    }
  }

  def delete(toDelete: FullName): Boolean = {
    storage.delete(fileId(toDelete.name + ".json"))
  }

  def exists(prefix: String): Boolean = {
    // storage.list is class A operation, storage.read class B - class A is much more expensive
    try {
      // note: storage.reader never fails
      val bytes = storage.readAllBytes(bucket, prefix)
      // note: there is currently no content, but the request should fail on non-existent file
      bytes.asInstanceOf[Unit]
      true
    } catch {
      case ex: StorageException =>
        false
    }
  }

  def enumerate(prefix: String): Iterable[(FullName, String)] = {
    val blobs = storage.list(bucket, BlobListOption.prefix(prefix))
    val list = blobs.iterateAll().asScala
    val actStream = for (iCandidate <- list) yield {
      val iName = iCandidate.getName
      assert(iName.startsWith(prefix))
      FullName(iName) -> iName.drop(prefix.length)
    }
    actStream.toVector  // toVector to avoid debugging streams, we are always traversing all of them anyway
  }

  def listAllItems(): Iterable[FileItem] = {

    val blobs = storage.list(bucket)
    val list = blobs.iterateAll().asScala
    list
  }

  def itemModified(item: FileItem) = Option(new java.util.Date(item.getUpdateTime))

  def deleteItem(item: FileItem) = storage.delete(item.getName)

  def itemName(item: FileItem): String = item.getName

}
