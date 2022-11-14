package com.github.opengrabeso.loctio

import com.avsystem.commons.serialization.GenCodec
import com.avsystem.commons.serialization.json.{JsonStringInput, JsonStringOutput}
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

  private def userFilename(namespace: String, filename: String) = FullName(namespace, filename)

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

  def input(filename: FullName): InputStream = {
    // TODO: check if any prefetch or other large data optimization can be used for GCS
    // we used openPrefetchingReadChannel with GAE / gcsService
    //val bufferSize = 1 * 1024 * 1024
    //val readChannel = gcsService.openPrefetchingReadChannel(fileId(filename.name), 0, bufferSize)
    val fid = fileId(filename.name)
    val readChannel = storage.reader(fid)

    Channels.newInputStream(readChannel)
  }

  def store(name: FullName, obj: AnyRef, metadata: (String, String)*): Unit = {
    //println(s"store '$name'")
    // TODO: pass Codec evidence instead
    getCodec(obj.getClass) match {
      case Some(codec) =>
        val os = output(name + ".json", metadata, contentType = "application/json")
        try {
          val json = JsonStringOutput.write(obj)(codec.asInstanceOf[GenCodec[AnyRef]])
          os.write(json.getBytes(StandardCharsets.UTF_8))
        } finally {
          os.close()
        }
      case None =>
        // new format not supported for the class yet - use Java serialization
        val os = output(name, metadata)
        val oos = try {
          new ObjectOutputStream(os)
        } catch {
          case ex: Throwable =>
            // normally oos.close handles this, but in case of new ObjectOutputStream failure we close it here
            os.close()
            throw ex
        }
        try {
          oos.writeObject(obj)
        } finally {
          oos.close()
        }
    }

  }

  def getFullName(stage: String, filename: String): FullName = {
    FullName(stage, filename)
  }


  private def readSingleObject[T: ClassTag](ois: ObjectInputStream): Option[T] = {
    try {
      val read = ois.readObject()
      read match {
        case r: T => Some(r)
        case null => None
        case any =>
          val classTag = implicitly[ClassTag[T]]
          throw new InvalidClassException(s"Read class ${any.getClass.getName}, expected ${classTag.runtimeClass.getName}")
      }
    } catch {
      case x: StorageException if x.getCode == 404 =>
        // reading a file which does not exist
        None
    }
  }

  private def loadCodec[T:  GenCodec](filename: FullName): Option[T] = {
    try {
      val bytes = storage.readAllBytes(bucket, filename.name)
      val s = new String(bytes, StandardCharsets.UTF_8)
      Some(JsonStringInput.read[T](s))
    } catch {
      case ex: StorageException if ex.getCode == 404 =>
        // we expect StorageException - file not found
        None
    }
  }

  private def loadRawName[T : ClassTag](filename: FullName): Option[T] = {
    //println(s"load '$filename' - '$userId'")
    val is = input(filename)
    try {
      val ois = new ObjectInputStream(is)
      try {
        readSingleObject[T](ois)
      } finally {
        ois.close()
      }
    } catch {
      case ex: IOException =>
        None
    } finally {
      is.close()
    }
  }

  def getCodec(c: Class[_]): Option[GenCodec[_]] = {
    if (c == classOf[UserList]) {
      Some(implicitly[GenCodec[UserList]])
    } else if (c == classOf[Presence.PresenceInfo]) {
      Some(implicitly[GenCodec[Presence.PresenceInfo]])
    } else {
      None
    }
  }

  def load[T : ClassTag](fullName: FullName): Option[T] = {
    // TODO: pass GenCodec evidence instead
    val codec = getCodec(classTag[T].runtimeClass)

    codec.flatMap { c =>
      loadCodec(fullName + ".json")(c).asInstanceOf[Option[T]]
    }.orElse {

      object FormatChanged {
        def unapply(arg: Exception): Option[Exception] = arg match {
          case _: java.io.InvalidClassException => Some(arg) // bad serialVersionUID
          case _: ClassNotFoundException => Some(arg) // class / package names changed
          case _: ClassCastException => Some(arg) // class changed (like Joda time -> java.time)
          case _ => None
        }
      }
      try {
        val loaded = loadRawName[T](fullName)
        if (codec.nonEmpty && loaded.nonEmpty) {
          // convert to a new (codec based) representation
          println(s"Convert ${fullName.name} to JSON")
          store(fullName, loaded.get.asInstanceOf[AnyRef])
        }
        loaded
      } catch {
        case x: StorageException if x.getCode == 404 =>
          None
        case FormatChanged(x) =>
          println(s"load error ${x.getMessage} - $fullName")
          storage.delete(fileId(fullName.name))
          None
        case _: java.io.EOFException =>
          println(s"Short (most likely empty) file $fullName")
          None
        //case ex: java.io.IOException =>

        case x: Exception =>
          x.printStackTrace()
          None
      }
    }
  }

  def delete(toDelete: FullName): Boolean = {
    storage.delete(fileId(toDelete.name))
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
