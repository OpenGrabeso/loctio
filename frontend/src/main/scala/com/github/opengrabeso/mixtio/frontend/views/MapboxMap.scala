package com.github.opengrabeso.mixtio
package frontend.views

import frontend.model._
import facade.UdashApp._
import facade.mapboxgl._
import facade._
import org.scalajs.dom

import scala.scalajs.js
import js.Dynamic.literal
import js.JSConverters._
import scalatags.JsDom.all._
import io.udash._

object MapboxMap extends common.Formatting {
  final val eventsName = "events" // used for both layer and source
  final val routeName = "route"

  private def onClick(handler: Event => Boolean) = {
    onclick :+= handler
  }

  implicit class LngLatOps(a: LngLat) {
    def - (b: LngLat): LngLat = new LngLat(lat = a.lat - b.lat, lng = a.lng - b.lng)
    def + (b: LngLat): LngLat = new LngLat(lat = a.lat + b.lat, lng = a.lng + b.lng)
    def * (x: Double): LngLat = new LngLat(lat = a.lat * x, lng = a.lng * x)
    def dotProduct(b: LngLat) = a.lng * b.lng + a.lat * b.lat
    def dist2(b: LngLat) = (a-b) dotProduct (a-b)
  }

  def display(routeData: Seq[(Double, Double, Double, Double)], events: Property[Seq[EditEvent]], presenter: edit.PagePresenter): Map = {
    // TODO: use tupled route representation directly instead of array one
    val route = routeData.map(t => js.Array(t._1, t._2, t._3, t._4)).toJSArray
    val routeX = route.map(_(0))
    val routeY = route.map(_(1))
    val minX = routeX.min
    val maxX = routeX.max
    val minY = routeY.min
    val maxY = routeY.max

    val bounds = new LngLatBounds(
      _ne = new LngLat(
        lat = minY,
        lng = minX
      ),
      _sw = new LngLat (
        lat = maxY,
        lng = maxX
      )
    )

    accessToken = mapBoxToken
    val map = new Map(js.Dynamic.literal(
      container = "map", // container id
      style = "mapbox://styles/ospanel/cjkbfwccz11972rmt4xvmvme6", // stylesheet location
      center = js.Array((minX + maxX) / 2, (minY + maxY) / 2), // starting position [lng, lat]
      zoom = 13 // starting zoom
    ))
    val fitOptions = literal(
      padding = 50
    )
    map.fitBounds(bounds, fitOptions)


    def moveHandler() = {
      val existing = map.getSource(eventsName)
      if (existing.isDefined) {
        val data = existing.get._data
        renderGrid(map, data.features.asInstanceOf[js.Array[js.Dynamic]](0).geometry.coordinates.asInstanceOf[js.Array[Double]])
      }
    }
    map.on("moveend", _ => moveHandler())
    map.on("move", _ => moveHandler())


    map.on("load", { _ =>
      renderRoute(map, route)
      renderEvents(map, events.get, route)
      renderGrid(map, route(0))

      map.on("mousemove", {re =>
        val e = re.asInstanceOf[MapMouseEvent]
        val features = map.queryRenderedFeatures(e.point, new js.Object { val layers = js.Array(eventsName) })
        val routeFeatures = map.queryRenderedFeatures(e.point, new js.Object {val layers = js.Array(routeName)})
        map.getCanvas().style.cursor = if (features.nonEmpty) "pointer" else if (routeFeatures.nonEmpty) "context-menu" else ""
      })

      var currentPopup: Popup = null

      def replacePopup(coord: LngLat)(init: Popup => Unit) = {
        hidePopup()
        val popup = new mapboxgl.Popup().setLngLat(coord)
        init(popup)
        popup.addTo(map)
        currentPopup = popup
      }
      def hidePopup() = {
        if (currentPopup != null) currentPopup.remove()
        currentPopup = null
      }
      map.on("click", (re) => {
        val e = re.asInstanceOf[MapMouseEvent]
        val features = map.queryRenderedFeatures(e.point, new js.Object {val layers = js.Array(eventsName)})
        val routeFeatures = map.queryRenderedFeatures(e.point, new js.Object {val layers = js.Array(routeName)})
        if (features.nonEmpty) {
          val feature = features.head

          val time = feature.properties.time.asInstanceOf[js.UndefOr[Int]]
          val description = feature.properties.description.asInstanceOf[js.UndefOr[String]]
          val title = feature.properties.title.asInstanceOf[String]
          println(s"$time, $description, $title")
          val showPopupAt = replacePopup(feature.geometry.coordinates)(_)
          if (description.contains("event") && time.isDefined) {

            // we generate DOM here, as passing it via feature.properties is not supported by Mapbox
            // see https://docs.mapbox.com/mapbox-gl-js/api#map#queryrenderedfeatures:
            // > For GeoJSON sources, only string and numeric property values are supported (i.e. null, Array, and Object values are not supported).
            showPopupAt { popup =>
              events.get.find(_.time == time.get).map { event =>
                popup.setDOMContent(
                  div(
                    EventView.getSelectHtml(event, title),
                    br(),
                    button(
                      `type` := "button",
                      "Delete",
                      onClick{ _ =>
                        presenter.deleteEvent(time.get)
                        hidePopup()
                        false
                      }
                    )
                  ).render
                )
              }.getOrElse {
                showPopupAt(_.setText(title))
              }
            }
          }

        } else if (routeFeatures.nonEmpty) {
          val coordinate = map.unproject(e.point)
          // use route, not feature.geometry.coordinates, because it contains time / distance as well
          val nearest = findNearestPoint(route, coordinate)
          replacePopup(coordinate) {
            _.setDOMContent(
              div(
                s"${displaySeconds(nearest(2).toInt)} (${displayDistance(nearest(3))})",
                br(),
                button(
                  `type` := "button",
                  // TODO: insert selection instead
                  "Create lap",
                  onClick { _ =>
                    presenter.createLap(nearest)
                    hidePopup()
                    false
                  }
                )
              ).render
            )
          }
        }

      })



    })

    map
  }

  def changeEvents(map: Map, e: Seq[EditEvent], route: Seq[(Double, Double, Double, Double)]): Unit = {
    val routeA = route.map(i => js.Array(i._1, i._2, i._3, i._4)).toJSArray

    val eventsData = mapEventData(e, routeA);
    val geojson = literal(
      `type` = "FeatureCollection",
      features = eventsData.toJSArray
    )

    map.getSource(eventsName).get.setData(geojson)

  }


  def renderRoute(map: Map, route: js.Array[js.Array[Double]]): Unit = {

    val routeLL = route.map(i => js.Array(i(0), i(1)))

    map.addSource(routeName, literal (
      `type` = "geojson",
      data = literal(
        `type` = "Feature",
        properties = literal(),
        geometry = literal(
          `type` = "LineString",
          coordinates = route // we provide additional data (distance, time) - this should do no harm
        )
      )
    ))

    map.addLayer(literal(
      id = routeName,
      `type` = "line",
      source = routeName,
      layout = literal(
        "line-join" -> "round",
        "line-cap" -> "round"
      ),
      paint = literal(
        "line-color" -> "#F44",
        "line-width" -> 3
      )
    ))
  }

  def lerp(a: Double, b: Double, f: Double) = {
    a + (b - a) * f
  }

  def findPoint(route: js.Array[js.Array[Double]], time: Double): js.Array[Double] = {
    // interpolate between close points if necessary
    val (before, after) = route.span(_(2) < time)
    if (before.isEmpty) after.head
    else if (after.isEmpty) before.last
    else {
      val prev = before.last
      val next = after.head
      val f: Double = if (time < prev(2)) 0
      else if (time > next(2)) 1
      else (time - prev(2)) / (next(2) - prev(2))
      js.Array(
        lerp(prev(0), next(0), f),
        lerp(prev(1), next(1), f),
        lerp(prev(2), next(2), f), // should be time
        lerp(prev(3), next(3), f)
      )
    }
  }

  def findNearestPoint(route: js.Array[js.Array[Double]], coord: LngLat): js.Array[Double] = {

    def nearestOnSegmentT(p: LngLat, v: LngLat, w: LngLat) = {
      val l2 = v dist2 w
      if (l2 == 0d) 0d
      else {
        val t = ((p - v) dotProduct (w - v)) / l2
        if (t < 0) 0d
        else if (t > 1) 1d
        else t
      }
    }

    // GeoJSON is longitude and latitude - see https://tools.ietf.org/html/rfc7946#section-3.1.1
    def lngLat(p: js.Array[Double]) = new LngLat(lng = p(0), lat = p(1))
    val segments = (route zip route.drop(1))
    val nearestPoints = segments.map { case (p0, p1) =>
      val lp0 = lngLat(p0)
      val lp1 = lngLat(p1)
      val t = nearestOnSegmentT(coord, lp0, lp1)
      val p = (lp1 - lp0) * t + lp0
      val dist2 = coord dist2 p
      (p, dist2, lerp(p0(2), p1(2), t), lerp(p0(3), p1(3), t))
    }
    val nearest = nearestPoints.minBy(_._2)
    val r = js.Array(nearest._1.lng, nearest._1.lat, nearest._3, nearest._4)
    println(s"nearest $nearest -> $r")
    r
  }


  def mapEventData(events: Seq[EditEvent], route: js.Array[js.Array[Double]]): Seq[js.Object] = {
    val dropStartEnd = events.drop(1).dropRight(1)
    val eventMarkers = dropStartEnd.map { e =>
      // ["split", 0, 0.0, "Run"]
      val r = findPoint(route, e.time)
      val time = displaySeconds(e.time)
      val eventTitle = if (e.action.nonEmpty) s"$time (${e.action})" else time
      val marker = literal(
        `type` = "Feature",
        geometry = literal(
          `type` = "Point",
          coordinates = js.Array(r(0), r(1))
        ),
        properties = literal(
          title = eventTitle,
          icon = "circle",
          description = "event",
          time = e.time,
          color = "#444",
          opacity = 0.5,
        )
      )
      marker
    }

    val routeHead = route.head
    val routeLast = route.last

    val startMarker = literal(
      `type` = "Feature",
      geometry = literal(
        `type` = "Point",
        coordinates = js.Array(routeHead(0), routeHead(1))
      ),
      properties = literal(
        title = "Begin",
        description = EventView.eventDescription(events.head).outerHTML,
        icon = "triangle",
        color = "#F22",
        opacity = 1
      )
    )
    val endMarker = literal(
      `type` = "Feature",
      geometry = literal(
        `type` = "Point",
        coordinates = js.Array(routeLast(0), routeLast(1))
      ),
      properties = literal(
        title = "End",
        description = EventView.eventDescription(events.last).outerHTML,
        icon = "circle",
        color = "#2F2",
        opacity = 0.5
      )
    )

    startMarker +: eventMarkers :+ endMarker
  }


  def renderEvents(map: Map, events: Seq[EditEvent], route: js.Array[js.Array[Double]]): Unit = {
    val markers = mapEventData(events, route)

    val iconLayout = literal(
      "icon-image" -> "{icon}-11",
      "text-field" -> "{title}",
      "text-font" -> js.Array("Open Sans Semibold", "Arial Unicode MS Bold"),
      "text-size" -> 10,
      "text-offset" -> js.Array(0, 0.6),
      "text-anchor" -> "top"
    )

    map.addSource(eventsName, literal(
      `type` = "geojson",
      data = literal(
        `type` = "FeatureCollection",
        features = markers.toJSArray
      )
    ))
    map.addLayer(literal(
      id = eventsName,
      `type` = "symbol",
      source = eventsName,
      layout = iconLayout
    ))
    var lastKm = 0.0
    val kmMarkers = route.flatMap {r =>
      val dist = r(3) / 1000
      val currKm = Math.floor(dist)
      if (currKm > lastKm) {
        val kmMarker = literal(
          `type` = "Feature",
          geometry = literal(
            `type` = "Point",
            coordinates = js.Array(r(0), r(1))
          ),
          properties = literal(
            title = s"${displaySeconds(r(2).toInt)} ($currKm km)",
            icon = "circle-stroked",
            color = "#2F2",
            opacity = 0.5
          )
        )
        lastKm = currKm
        Some(kmMarker)
      } else None
    }
    map.addSource("kms", literal(
      `type` = "geojson",
      data = literal(
        `type` = "FeatureCollection",
        features = kmMarkers.toJSArray
      )
    ))
    map.addLayer(literal(
      id = "kms",
      `type` = "symbol",
      source = "kms",
      layout = iconLayout
    ))
  }

  case class GridAndAlpha(grid: js.Array[js.Array[js.Array[Double]]], alpha: Double)

  def generateGrid(bounds: LngLatBounds, size: Size, fixedPoint: js.Array[Double]): GridAndAlpha = {
    // TODO: pad the bounds to make sure we draw the lines a little longer
    val grid_box = bounds
    val avg_y = (grid_box._ne.lat + grid_box._sw.lat) * 0.5
    // Meridian length is always the same
    val meridian = 20003930.0
    val equator = 40075160
    val parallel = Math.cos(avg_y * Math.PI / 180) * equator
    val grid_distance = 1000.0
    val grid_step_x = grid_distance / parallel * 360
    val grid_step_y = grid_distance / meridian * 180
    val minSize = Math.max(size.x, size.y)
    val minLineDistance = 10
    val maxLines = minSize / minLineDistance
    val latLines = _latLines(bounds, fixedPoint, grid_step_y, maxLines)
    val lngLines = _lngLines(bounds, fixedPoint, grid_step_x, maxLines)
    val alpha = Math.min(latLines.alpha, lngLines.alpha)
    if (latLines.lines.length > 0 && lngLines.lines.length > 0) {
      val grid = latLines.lines.flatMap(i => if (Math.abs(i) > 90) {
          None
        } else {
          Some(_horizontalLine(bounds, i, alpha))
        })
      grid ++= lngLines.lines.map { i =>
        _verticalLine(bounds, i, alpha)
      }
      return GridAndAlpha(grid, alpha)
    }
    GridAndAlpha(js.Array(), 0)
  }

  case class Size(x: Double, y: Double)

  def renderGrid(map: Map, fixedPoint: js.Array[Double]): Unit = {
    val container = map.getContainer()
    val size = Size(
      x = container.clientWidth,
      y = container.clientHeight
    )
    val gridAndAlpha = generateGrid(map.getBounds(), size, fixedPoint)
    val grid = gridAndAlpha.grid
    val alpha = gridAndAlpha.alpha
    val gridData = new js.Object {
      var `type` = "Feature"
      var properties = new js.Object {}
      var geometry = new js.Object {
        var `type` = "MultiLineString"
        var coordinates = grid
      }
    }
    val existing = map.getSource("grid")
    if (existing.isDefined) {
      existing.get.setData(gridData)
      map.setPaintProperty("grid", "line-opacity", alpha)
      map.setLayoutProperty("grid", "visibility", if (alpha > 0) "visible" else "none")
    } else {
      map.addSource("grid", new js.Object {
        var `type` = "geojson"
        var data = gridData
      })
      map.addLayer(new js.Object {
        var id = "grid"
        var `type` = "line"
        var source = "grid"
        var layout = new js.Object {
          var `line-join` = "round"
          var `line-cap` = "round"
        }
        var paint = new js.Object {
          var `line-color` = "#e40"
          var `line-width` = 2
          var `line-opacity` = alpha
        }
      })
    }
    // icon list see https://www.mapbox.com/maki-icons/ or see https://github.com/mapbox/mapbox-gl-styles/tree/master/sprites/basic-v9/_svg
    // basic geometric shapes, each also with - stroke variant:
    //   star, star-stroke, circle, circle-stroked, triangle, triangle-stroked, square, square-stroked
    //
    // specific, but generic enough:
    //   marker, cross, heart (Maki only?)
  }

  private case class AlphaLines(alpha: Double, lines: js.Array[Double])

  private def _latLines(bounds: LngLatBounds, fixedPoint: js.Array[Double], yticks: Double, maxLines: Double): AlphaLines = {
    _lines(bounds._sw.lat, bounds._ne.lat, yticks, maxLines, fixedPoint(1))
  }

  private def _lngLines(bounds: LngLatBounds, fixedPoint: js.Array[Double], xticks: Double, maxLines: Double): AlphaLines = {
    _lines(bounds._sw.lng, bounds._ne.lng, xticks, maxLines, fixedPoint(0))
  }

  private def _lines(low: Double, high: Double, ticks: Double, maxLines: Double, fixedCoord: Double): AlphaLines = {
    val delta = high - low
    val lowAligned = Math.floor((low - fixedCoord) / ticks) * ticks + fixedCoord
    val lines = new js.Array[Double]
    if (delta / ticks <= maxLines) {
      var i = lowAligned;
      while (i <= high) {
        lines.push(i)
        i += ticks
      }
    }
    val aScale = 15
    val a = maxLines / aScale / (delta / ticks)
    AlphaLines(
      lines = lines,
      alpha = Math.min(1, Math.sqrt(a))
    )
  }

  private def _verticalLine(bounds: LngLatBounds, lng: Double, alpha: Double) = {
    js.Array(js.Array(lng, bounds.getNorth()), js.Array(lng, bounds.getSouth()))
  }

  private def _horizontalLine(bounds: LngLatBounds, lat: Double, alpha: Double) = {
    js.Array(js.Array(bounds.getWest(), lat), js.Array(bounds.getEast(), lat))
  }
}
