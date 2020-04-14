package com.github.opengrabeso.mixtio.frontend.views.select

import io.udash.{ModelProperty, ReadableModelProperty, Url}
import io.udash.utils.FileUploader
import org.scalajs.dom.{Event, File, ProgressEvent, html}
import org.scalajs.dom.raw.{FormData, XMLHttpRequest}

import scala.scalajs.js

// modified [[io.udash.utils.FileUploader]]
class ActivityUploader(url: Url) {
  import FileUploader._

  /** Uploads provided `files` in a field named `fieldName`. */
  def upload(
    fieldName: String, files: Seq[File], extraData: Map[js.Any, js.Any] = Map.empty
  ): ReadableModelProperty[FileUploadModel] = {
    // TODO: use REST API instead of XMLHttpRequest or parse the XMLHttpRequest response
    val p = ModelProperty[FileUploadModel](
      new FileUploadModel(Seq.empty, FileUploadState.InProgress, 0, 0, None)
    )
    val data = new FormData()

    extraData.foreach { case (key, value) => data.append(key, value) }
    files.foreach(file => {
      data.append(fieldName, file)
      p.subSeq(_.files).append(file)
    })

    val xhr = new XMLHttpRequest
    xhr.upload.addEventListener("progress", (ev: ProgressEvent) =>
      if (ev.lengthComputable) {
        p.subProp(_.bytesSent).set(ev.loaded)
        p.subProp(_.bytesTotal).set(ev.total)
      }
    )
    xhr.addEventListener("load", (ev: Event) => {
      p.subProp(_.response).set(Some(new HttpResponse(xhr)))
      p.subProp(_.state).set(xhr.status / 100 match {
        case 2 =>
          FileUploadState.Completed
        case _ =>
          FileUploadState.Failed
      })
    }
    )
    xhr.addEventListener("error", (_: Event) => p.subProp(_.state).set(FileUploadState.Failed))
    xhr.addEventListener("abort", (_: Event) => p.subProp(_.state).set(FileUploadState.Cancelled))
    xhr.open(method = "POST", url = url.value)
    xhr.send(data)

    p
  }
}