package com.github.opengrabeso.loctio.common

import FileStore.FullName

import scala.collection.mutable
import scala.reflect.ClassTag

/**
  * Simulated storage for testing
  * */
object TestStorage extends FileStore {
  case class FileItem(name: String, content: AnyRef, modified: java.util.Date, metadata: Seq[(String, String)])

  private val files = mutable.Map.empty[String,FileItem]

  def itemModified(fileItem: FileItem) = files.get(fileItem.name).map(_.modified)
  def listAllItems() = files.values

  def deleteItem(item: FileItem) = files.remove(item.name)
  def store(name: FileStore.FullName, obj: AnyRef, metadata: (String, String)*) = {
    val now = new java.util.Date()
    files += name.name -> FileItem(name.name, obj, now, metadata)
  }
  def load[T: ClassTag](name: FullName): Option[T] = {
    files.get(name.name).map(_.content.asInstanceOf[T])
  }

  def itemName(item: FileItem) = item.name
  def metadata(name: FullName) = files.get(name.name).toSeq.flatMap(_.metadata)
  def updateMetadata(item: FullName, metadata: Seq[(String, String)]) = {
    files.get(item.name).exists { f =>
      files += item.name -> f.copy(metadata = metadata)
      true
    }
  }
}
