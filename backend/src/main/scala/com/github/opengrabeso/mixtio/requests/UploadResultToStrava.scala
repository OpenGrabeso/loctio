package com.github.opengrabeso.mixtio
package requests

import com.google.api.client.http.HttpResponseException
import com.google.appengine.api.taskqueue._

import scala.util.{Failure, Success}
import scala.xml.NodeSeq

sealed trait UploadStatus {
  def xml: NodeSeq
  override def toString: String = xml.toString
}

@SerialVersionUID(10)
case class UploadInProgress(uploadId: Long) extends UploadStatus {
  def xml = Nil
}

@SerialVersionUID(10)
case class UploadDone(stravaId: Long) extends UploadStatus {
  def xml = <done>{stravaId}</done>
}

@SerialVersionUID(10)
case class UploadDuplicate(dupeId: Long) extends UploadStatus {
  def xml = <duplicate>{dupeId}</duplicate>
}

@SerialVersionUID(10)
case class UploadError(ex: Throwable) extends UploadStatus {
  def xml = <error>{ex.getMessage}</error>

}
// background push queue task

@SerialVersionUID(10L)
case class UploadResultToStrava(key: String, auth: Main.StravaAuthResult, sessionId: String) extends DeferredTask {

  def run()= {

    val api = new strava.StravaAPI(auth.token)

    val uploadNamespace = Main.namespace.upload(sessionId)
    val uploadResultNamespace = Main.namespace.uploadResult(sessionId)

    for (upload <- Storage.load2nd[Main.ActivityEvents](Storage.getFullName(uploadNamespace, key, auth.userId))) {

      val export = FitExport.export(upload)

      val ret = api.uploadRawFileGz(export, "fit.gz")

      Storage.store(Storage.FullName(uploadResultNamespace, key, auth.userId), ret)
      ret match {
        case UploadInProgress(uploadId) =>
          println(s"Upload started $uploadId")
          Storage.store(Storage.FullName(uploadResultNamespace, key, auth.userId), UploadInProgress(uploadId))
          val eta = System.currentTimeMillis() + 3000
          BackgroundTasks.addTask(WaitForStravaUpload(key, uploadId, auth, eta, sessionId))
        case done =>
          Storage.store(Storage.FullName(uploadResultNamespace, key, auth.userId), done)
      }
      Storage.delete(Storage.FullName(uploadNamespace, key, auth.userId))
    }

  }

}

case class WaitForStravaUpload(key: String, id: Long, auth: Main.StravaAuthResult, eta: Long, sessionId: String) extends DeferredTask {
  private def retry(nextEta: Long) = {
    BackgroundTasks.addTask(WaitForStravaUpload(key, id, auth, nextEta, sessionId))
  }

  def run() = {
    // check if the upload has finished
    // Strava recommends polling no more than once a second
    val now = System.currentTimeMillis()
    if (now < eta) {
      retry(eta)
    } else {
      val api = new strava.StravaAPI(auth.token)
      val uploadResultNamespace = Main.namespace.uploadResult(sessionId)
      val done = api.activityIdFromUploadId(id)
      done match {
        case UploadInProgress(_) =>
          // still processing - retry
          retry(now + 2000)
        case _ =>
          Storage.store(Storage.FullName(uploadResultNamespace, key, auth.userId), done)

      }

    }
  }

}