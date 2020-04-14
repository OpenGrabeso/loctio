package com.github.opengrabeso.mixtio
package frontend.views

import common.model._

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

abstract class PendingUploads[ActId](implicit ec: ExecutionContext) {
  var pending = Map.empty[String, Set[ActId]]

  private final val pollPeriodMs = 1000

  def sendToStrava(fileIds: Seq[ActId]): Future[Seq[(ActId, String)]]

  def delay(milliseconds: Int): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(milliseconds) {
      p.success(())
    }
    p.future
  }


  def startUpload(api: rest.UserRestAPI, fileIds: Seq[ActId]) = {

    val setOfFileIds = fileIds.toSet
    // set all files as uploading before starting the API to make the UI response immediate
    setUploadProgressFile(setOfFileIds, true, "")

    val uploadStarted = sendToStrava(fileIds)

    uploadStarted.foreach { a =>
      val add = a.toMap
      // some activities might be discarded, fileId is not guaranteed to match fileToPending
      // remove uploading status for the files for which no upload has started
      setUploadProgressFile(setOfFileIds -- add.keySet, false, "")

      add.foreach { case (id, i) =>
        println(s"Upload $i started for $id")
        pending += pending.get(i).map { addTo =>
          i -> (addTo + id)
        }.getOrElse {
          i -> Set(id)
        }
      }
      println(s"pending ${pending.size} (added ${add.size})")
      if (pending.nonEmpty) {
        delay(pollPeriodMs).foreach(_ => checkPendingResults(api))
      }
    }

  }

  def checkPendingResults(api: rest.UserRestAPI): Unit = {
    for {
      status <- api.pollUploadResults(pending.keys.toSeq, facade.UdashApp.sessionId)
    } {
      for (result <- status) {
        result match {
          case UploadProgress.Pending(uploadId) =>
          case UploadProgress.Done(stravaId, uploadId) =>
            println(s"$uploadId completed with $result")
            setStrava(uploadId, Some(FileId.StravaId(stravaId)))
            setUploadProgress(uploadId, false, "")
            pending -= uploadId
          case UploadProgress.Error(uploadId, error) =>
            println(s"$uploadId completed with error $error")
            // TODO: check and handle most probable error cause: a duplicate activity
            setUploadProgress(uploadId, true, error)
            pending -= uploadId
          case UploadProgress.Duplicate(uploadId, dupeStravaId) =>
            println(s"$uploadId completed as duplicate of $dupeStravaId")
            setUploadProgress(uploadId, true, s"Duplicate of <a href=https://www.strava.com/activities/$dupeStravaId>$dupeStravaId</a>")
            pending -= uploadId
        }
      }
      if (pending.nonEmpty) {
        delay(pollPeriodMs).foreach(_ => checkPendingResults(api))
      }
    }
  }

  def setStrava(uploadId: String, stravaId: Option[FileId.StravaId]): Unit = {
    for (fileId <- pending.get(uploadId)) {
      setStravaFile(fileId, stravaId)
    }
  }

  def setUploadProgress(uploadId: String, uploading: Boolean, uploadState: String): Unit = {
    for (fileId <- pending.get(uploadId)) {
      setUploadProgressFile(fileId, uploading, uploadState)
    }
  }

  def setUploadProgressFile(fileId: Set[ActId], uploading: Boolean, uploadState: String): Unit
  def setStravaFile(fileId: Set[ActId], stravaId: Option[FileId.StravaId]): Unit

}
