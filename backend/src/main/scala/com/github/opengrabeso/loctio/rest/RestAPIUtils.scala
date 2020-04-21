package com.github.opengrabeso.loctio.rest

import com.github.opengrabeso.loctio.requests.BackgroundTasks

import scala.concurrent.{ExecutionContext, Future}

trait RestAPIUtils {
  def syncResponse[T](t: =>T): Future[T] = {
    Future.successful(t)
  }

  protected def createEC(): ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable) = {
      val threadFactory = BackgroundTasks.currentRequestThreadFactory
      val t = threadFactory.newThread(runnable)
      t.start()
    }
    def reportFailure(cause: Throwable) = cause.printStackTrace()
  }
  // note: Not very efficient, there is no thread pool. Use only for heavy-weight tasks
  def asyncResponse[T](t: =>T): Future[T] = {
    val threadFactory = BackgroundTasks.currentRequestThreadFactory

    implicit val ec = createEC()
    Future {
      t
    }

  }


}
