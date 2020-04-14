package com.github.opengrabeso.mixtio
package requests
import com.google.appengine.api.taskqueue.{DeferredTask, QueueFactory, TaskOptions}
import spark.{Request, Response}
import java.time.ZonedDateTime

/**
  * Regular cleanup performed periodically, for all users, not requiring user access information
  * */
object Cleanup extends DefineRequest("/cleanup") {

  @SerialVersionUID(10L)
  case object BackgroundCleanup extends DeferredTask {
    override def run(): Unit = {
      // catch all exceptions, because otherwise the task would be repeated on a failure
      // as the task is periodic, this is not necessary - moreover the failure is most likely
      // caused by a bug and would happen again
      try {
        val cleanedCloudStorage = Storage.cleanup()
        println(s"Cleaned $cleanedCloudStorage storage items")
      } catch {
        case ex: Exception =>
          println(s"Exception $ex during cleanup")
      }
    }
  }


  def html(request: Request, resp: Response) = {
    val periodic = request.queryParams("periodic")
    if (periodic != null) {

      BackgroundTasks.addTask(BackgroundCleanup)

      <cleaned><deferred>Background request initiated</deferred></cleaned>
    } else {
      println("Unknown cleanup type")
      resp.status(400) // Bad Request
      <cleaned><error>Syntax error</error></cleaned>
    }
  }
}
