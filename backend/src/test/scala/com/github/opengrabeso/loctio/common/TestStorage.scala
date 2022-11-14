package com.github.opengrabeso.loctio.common

import FileStore.FullName
import com.avsystem.commons.serialization.GenCodec

import scala.collection.mutable
import scala.reflect.ClassTag

/**
  * Simulated storage for testing
  * */
class TestStorage extends FileStore {
  case class FileItem(name: String, content: Any, modified: java.util.Date, metadata: Seq[(String, String)])

  private val files = mutable.Map.empty[String,FileItem]

  def itemModified(fileItem: FileItem) = files.get(fileItem.name).map(_.modified)
  def listAllItems() = files.values

  def deleteItem(item: FileItem) = files.remove(item.name)
  def store[T: GenCodec](name: FileStore.FullName, obj: T, metadata: (String, String)*) = {
    val now = new java.util.Date()
    files += name.name -> FileItem(name.name, obj, now, metadata)
  }
  def load[T: ClassTag: GenCodec](name: FullName): Option[T] = {
    files.get(name.name).map(_.content.asInstanceOf[T])
  }

  def itemName(item: FileItem) = item.name
}
