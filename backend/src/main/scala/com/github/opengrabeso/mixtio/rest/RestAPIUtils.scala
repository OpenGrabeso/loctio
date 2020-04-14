package com.github.opengrabeso.mixtio.rest

import com.github.opengrabeso.mixtio.requests.BackgroundTasks

import scala.concurrent.{ExecutionContext, Future}

trait RestAPIUtils {
  def syncResponse[T](t: =>T): Future[T] = {
    Future.successful(t)
  }
  // note: Not very efficient, there is no thread pool. Use only for heavy-weight tasks
  def asyncResponse[T](t: =>T): Future[T] = {
    val threadFactory = BackgroundTasks.currentRequestThreadFactory

    implicit object EC extends ExecutionContext {
      def execute(runnable: Runnable) = {
        val t = threadFactory.newThread(runnable)
        t.start()
      }
      def reportFailure(cause: Throwable) = cause.printStackTrace()
    }
    Future {
      t
    }

  }


}
