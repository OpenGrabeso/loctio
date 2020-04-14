package com.github.opengrabeso.mixtio
package frontend
package views
package edit

import model._
import facade.UdashApp
import routing._
import io.udash._
import common.model._
import org.scalajs.dom
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExportTopLevel

/** Contains the business logic of this view. */
class PagePresenter(
  model: ModelProperty[PageModel],
  userContextService: services.UserContextService,
  application: Application[RoutingState]
)(implicit ec: ExecutionContext) extends Presenter[EditPageState] {


  /** We don't need any initialization, so it's empty. */
  override def handleState(state: EditPageState): Unit = {
  }

  private def eventsToSend = {
    val events = model.subProp(_.events).get
    val eventsToSend = events.map { e =>
      if (!e.active) ("delete", e.time)
      else if (e.action == "lap") (e.action, e.time)
      else if (e.boundary) (e.action, e.time)
      else ("", e.time)
    }
    eventsToSend
  }

  def toggleSplitDisable(time: Int): Unit = {
    val events = model.subProp(_.events).get
    val from = events.dropWhile(_.time < time)
    for (first <- from.headOption) {
      val togglingOff = first.active
      val toggle = first +: (if (togglingOff) {
        from.drop(1).takeWhile(e => !e.boundary)
      } else {
        from.drop(1).takeWhile(e => !e.boundary && !e.active)
      })

      val toggleTimes = toggle.map(_.time).toSet
      val toggled = events.map { e =>
        if (toggleTimes contains e.time) e.copy(active = !e.active)
        else e
      }
      model.subProp(_.events).set(toggled)
    }
  }

  def download(time: Int): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      for (data <- userContextService.api.get.downloadEditedActivity(fileId, UdashApp.sessionId, eventsToSend, time)) {
        val bArray = js.typedarray.Int8Array.from(data.data.toJSArray)
        val blob = new dom.Blob(js.Array(bArray), dom.BlobPropertyBag(`type` = "application/octet-stream"))
        Download.download(blob, "download.fit", "application/octet-stream")
      }
    }
  }

  def sendToStrava(time: Int): Unit = {
    for (fileId <- model.subProp(_.merged).get) {
      uploads.startUpload(userContextService.api.get, Seq(time))

      // uploads identified by the starting time
      // it could be much simpler, as we are starting one upload each time
      // however we share PendingUploads with the `select` which initiates multiple uploads at the same time
      object uploads extends PendingUploads[Int] {

        def sendToStrava(fileIds: Seq[Int]) = {
          userContextService.api.get.sendEditedActivityToStrava(fileId, UdashApp.sessionId, eventsToSend, time).map {
            _.toSeq.map(time -> _)
          }
        }

        def modifyActivities(fileId: Set[Int])(modify: EditEvent => EditEvent): Unit = {
          if (fileId.nonEmpty) model.subProp(_.events).set {
            model.subProp(_.events).get.map { e =>
              if (fileId contains e.time) {
                modify(e)
              } else e
            }
          }
        }

        def setStravaFile(fileId: Set[Int], stravaId: Option[FileId.StravaId]): Unit = {
          modifyActivities(fileId)(_.copy(strava = stravaId))
        }

        def setUploadProgressFile(fileId: Set[Int], uploading: Boolean, uploadState: String): Unit = {
          modifyActivities(fileId)(_.copy(uploading = uploading, uploadState = uploadState))
        }

      }
    }
  }

  def uploadAll(): Unit = {
    for (e <- model.subProp(_.events).get) {
      if (e.shouldBeUploaded) sendToStrava(e.time)
    }
  }

  def createLap(coord: js.Array[Double]): Unit = {
    val lng = coord(0)
    val lat = coord(1)
    val time = coord(2).toInt
    val dist = coord(3)
    model.subProp(_.events).set {
      //val start = activityHeader = model.subProp(_.events.head)
      val events = model.subProp(_.events).get
      val start = events.head.event.stamp
      val event = LapEvent(start.plusSeconds(time))
      //val addLap = EditEvent()
      // TODO: use cleanupEvents instead
      // TODO: inherit "active" from surrounding events
      (EditEvent(start, event, dist) +: events).sortBy(_.time)
    }
  }

  def deleteEvent(time: Int): Unit = {
    model.subProp(_.events).set {
      model.subProp(_.events).get.filterNot(_.time == time)
    }
  }


  def isCheckedLap(e: EditEvent) = {
    e.action == "lap"
  }
  // was test original event state

  def wasUserLap(e: EditEvent) = {
    e.event.originalEvent == "lap"
  }

  def wasLongPause(e: EditEvent) = {
    e.event.originalEvent.lastIndexOf("long pause") == 0
  }

  def wasAnyPause(e: EditEvent) = {
    e.event.originalEvent == "pause" || wasLongPause(e)
  }

  def wasSegment(e: EditEvent) = {
    e.event.originalEvent.lastIndexOf("segment") == 0 || e.event.originalEvent.lastIndexOf("private segment") == 0
  }

  def wasHill(e: EditEvent) = {
    e.event.originalEvent == "elevation"
  }

  private def actionByPredicate(f: EditEvent => Boolean, newAction: String = "lap"): Unit = {
    model.subProp(_.events).set {
      model.subProp(_.events).get.map { e =>
        if (f(e)) e.copy(action = newAction)
        else e
      }
    }
  }

  def lapsSelectUser(): Unit = {
    actionByPredicate(wasUserLap)
  }

  def lapsSelectLongPauses(): Unit = {
    actionByPredicate(wasLongPause)
  }

  def lapsSelectAllPauses(): Unit = {
    actionByPredicate(wasAnyPause)
  }

  def lapsSelectHills(): Unit = {
    actionByPredicate(wasHill)
  }

  def removeAllLaps(): Unit = {
    actionByPredicate(isCheckedLap, "")
  }

  def testPredicate(f: EditEvent => Boolean): ReadableProperty[Boolean] = {
    model.subProp(_.events).transform(e => !e.exists(f))
  }

  def singleUploadAction: ReadableProperty[Option[String]] = model.subProp(_.events).transform { events =>
    val splits = events.filter(_.shouldBeUploaded)
    if (splits.size == 1) splits.headOption.map(_.action) else None
  }

  def testPredicateUnchecked(f: EditEvent => Boolean): ReadableProperty[Boolean] = {
    model.subProp(_.events).transform(events => !events.exists(e => f(e) && !isCheckedLap(e)))
  }
}
