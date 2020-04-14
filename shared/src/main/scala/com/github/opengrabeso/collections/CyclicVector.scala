package com.github.opengrabeso.collections

import java.util.NoSuchElementException

object CyclicVector {
  def empty[T] = new CyclicVector[T](Vector.empty, 0, 0)
}

case class CyclicVector[T](private val data: Vector[T], private val headIndex: Int, size: Int) {

  def modIndex(i: Int) = if (data.nonEmpty) i % data.size else 0

  def :+ (item: T) = {
    if (size >= data.size) {
      new CyclicVector[T](data.patch(modIndex(headIndex + size), Seq(item), 0), (headIndex + 1) % (data.size + 1), size + 1)
    } else {
      new CyclicVector[T](data.patch(modIndex(headIndex + size), Seq(item), 1), headIndex, size + 1)
    }
  }

  def tail = new CyclicVector[T](data, modIndex(headIndex + 1), size - 1)
  def isEmpty = size == 0
  def head = if (isEmpty) throw new NoSuchElementException else data(headIndex)
  def last = data(modIndex(headIndex + size - 1))
}
