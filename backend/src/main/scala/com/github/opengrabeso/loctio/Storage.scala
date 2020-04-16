package com.github.opengrabeso.loctio

import java.io._
import java.nio.channels.Channels

import com.google.auth.oauth2.GoogleCredentials

import collection.JavaConverters._
import com.google.cloud.storage.{Option => GCSOption, _}
import com.google.cloud.storage.Storage._
import org.apache.commons.io.IOUtils

import scala.reflect.ClassTag

import common.FileStore.FullName

object Storage extends common.FileStore {
  // from https://cloud.google.com/appengine/docs/standard/java/using-cloud-storage
  final val bucket = "loctio.appspot.com"

  type FileItem = Blob

  private def fileId(filename: String) = BlobId.of(bucket, filename)

  private def userFilename(namespace: String, filename: String) = FullName(namespace, filename)

  val credentials = GoogleCredentials.getApplicationDefault
  val storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService

  def output(filename: FullName, metadata: Seq[(String, String)]): OutputStream = {
    val fid = fileId(filename.name)
    val instance = BlobInfo.newBuilder(fid)
      .setMetadata(metadata.toMap.asJava)
      .setContentType("application/octet-stream").build()
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

  def store(name: FullName, obj: AnyRef, metadata: (String, String)*) = {
    //println(s"store '$name'")
    val os = output(name, metadata)
    val oos = new ObjectOutputStream(os)
    oos.writeObject(obj)
    oos.close()
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

  def loadRawName[T : ClassTag](filename: FullName): Option[T] = {
    //println(s"load '$filename' - '$userId'")
    val is = input(filename)
    try {
      val ois = new ObjectInputStream(is)
      readSingleObject[T](ois)
    } finally {
      is.close()
    }

  }

  def load[T : ClassTag](fullName: FullName): Option[T] = {
    object FormatChanged {
      def unapply(arg: Exception): Option[Exception] = arg match {
        case _: java.io.InvalidClassException => Some(arg) // bad serialVersionUID
        case _: ClassNotFoundException => Some(arg) // class / package names changed
        case _: ClassCastException => Some(arg) // class changed (like Joda time -> java.time)
        case _ => None
      }
    }
    try {
      loadRawName(fullName)
    } catch {
      case x: StorageException if x.getCode == 404 =>
        None
      case FormatChanged(x) =>
        println(s"load error ${x.getMessage} - $fullName")
        storage.delete(fileId(fullName.name))
        None
      case x: Exception =>
        x.printStackTrace()
        None
    }
  }

  def delete(toDelete: FullName): Boolean = {
    storage.delete(fileId(toDelete.name))
  }


  def enumerate(prefix: String, filter: Option[String => Boolean] = None): Iterable[(FullName, String)] = {
    val blobs = storage.list(bucket, BlobListOption.prefix(prefix))
    val list = blobs.iterateAll().asScala
    val actStream = for (iCandidate <- list) yield {
      val iName = iCandidate.getName
      assert(iName.startsWith(prefix))
      FullName(iName) -> iName.drop(prefix.length)
    }
    actStream.toVector  // toVector to avoid debugging streams, we are always traversing all of them anyway
  }

  def enumerateAll(): Iterable[String] = {
    val prefix = ""
    val blobs = storage.list(bucket, BlobListOption.prefix(prefix))
    val list = blobs.iterateAll().asScala
    for (i <- list) yield {
      assert(i.getName.startsWith(prefix))
      val name = i.getName.drop(prefix.length)
      name
    }
  }

  def metadata(name: FullName): Seq[(String, String)] = {
    val prefix = name
    val blobs = storage.list(bucket, BlobListOption.prefix(prefix.name))
    val found = blobs.iterateAll().asScala

    // there should be at most one result
    found.toSeq.flatMap { i =>
      assert(i.getName.startsWith(prefix.name))
      val m = try {
        val md = storage.get(bucket, i.getName, BlobGetOption.fields(BlobField.METADATA))
        md.getMetadata.asScala.toSeq
      } catch {
        case e: Exception =>
          e.printStackTrace()
          Nil
      }
      m
      //println(s"enum '$name' - '$userId': md '$m'")
    }
  }

  def metadataValue(item: FullName, name: String): Option[String] = {
    val md = metadata(item)
    md.find(_._1 == name).map(_._2)
  }

  def updateMetadata(item: FullName, metadata: Seq[(String, String)]): Boolean = {
    val blobId = fileId(item.name)
    val md = storage.get(blobId, BlobGetOption.fields(BlobField.METADATA))
    val userData = Option(md.getMetadata).getOrElse(new java.util.HashMap[String, String]).asScala
    val matching = metadata.forall { case (key, name) =>
      userData.get(key).contains(name)
    }
    if (!matching) {
      val md = userData ++ metadata
      val blobInfo = BlobInfo
        .newBuilder(blobId)
        .setMetadata(md.asJava)
        .build()
      storage.update(blobInfo)
    }
    !matching
  }

  def move(oldName: String, newName: String) : Unit = {
    if (oldName != newName) {
      val gcsFilenameOld = fileId(oldName)
      val gcsFilenameNew = fileId(newName)

      // read metadata
      val in = input(FullName(oldName))

      val md = storage.get(bucket, oldName, BlobGetOption.fields(BlobField.METADATA))
      val metadata = md.getMetadata

      val instance = BlobInfo.newBuilder(gcsFilenameNew)
        .setMetadata(metadata)
        .setContentType("application/octet-stream").build()
      val channel = storage.writer(instance)

      val output = Channels.newOutputStream(channel)
      try {
        IOUtils.copy(in, output)
        storage.delete(gcsFilenameOld)
      } finally {
        output.close()
      }
    }
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
