package com.github.opengrabeso.loctio.shared

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object FutureAt {
  val global = ExecutionContext.global

  implicit class ExecutionContextFuture(ec: ExecutionContext) {
    def future[T](code: => T): Future[T] = Future(code)(ec)
  }

  class AtOps[T](future: Future[T])(ec: ExecutionContext) {
    def foreach[U](f: T => U): Unit = future.foreach(f)(ec)
    def map[S](f: T => S): Future[S] = future.map(f)(ec)
    def flatMap[S](f: T => Future[S]): Future[S] = future.flatMap(f)(ec)
    def recover[U >: T](pf: PartialFunction[Throwable, U]): Future[U] = future.recover(pf)(ec)
    def onComplete[U](f: Try[T] => U): Unit = future.onComplete(f)(ec)
    def transform[S](f: Try[T] => Try[S]): Future[S] = future.transform(f)(ec)
  }

  implicit class FutureOps[T](future: Future[T]) {
    def at(ec: ExecutionContext) = new AtOps(future)(ec)
  }

  object executeNow extends ExecutionContext {
    def execute(runnable: Runnable) = {
      runnable.run()
    }
    def reportFailure(cause: Throwable) = {
      cause.printStackTrace()
    }

  }
}
