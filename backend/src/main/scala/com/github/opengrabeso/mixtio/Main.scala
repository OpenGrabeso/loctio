package com.github.opengrabeso.mixtio

import java.io.InputStream
import java.security.MessageDigest
import java.util
import java.util.Properties
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

import com.google.api.client.http.{GenericUrl, HttpRequest}
import com.google.api.client.http.json.JsonHttpContent
import com.fasterxml.jackson.databind.JsonNode
import com.google.api.client.json.jackson2.JacksonFactory

import scala.collection.JavaConverters._
import common.Util._
import common.model._
import shared.Timing

import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.immutable.SortedMap
import scala.util.control.Breaks._
import scala.xml.Node

object Main extends common.Formatting {

  import RequestUtils._

  private val md = MessageDigest.getInstance("SHA-256")

  def digest(bytes: Array[Byte]): String = {
    val digestBytes = (0:Byte) +: md.digest(bytes) // prepend 0 byte to avoid negative sign
    BigInt(digestBytes).toString(16)
  }

  def digest(str: String): String = digest(str.getBytes)

  case class SecretResult(appId: String, appSecret: String, mapboxToken: String, darkSkySecret: String, error: String)

  def secret: SecretResult = {
    val filename = "/secret.txt"
    try {
      val secretStream = Main.getClass.getResourceAsStream(filename)
      val lines = scala.io.Source.fromInputStream(secretStream).getLines
      SecretResult(lines.next(), lines.next(), lines.next(), lines.next(), "")
    } catch {
      case _: NullPointerException => // no file found
        SecretResult("", "", "", "", s"Missing $filename, app developer should check README.md")
      case _: Exception =>
        SecretResult("", "", "", "", s"Bad $filename, app developer should check README.md")
    }
  }

  def devMode: Boolean = {
    val prop = new Properties()
    prop.load(getClass.getResourceAsStream("/config.properties"))
    prop.getProperty("devMode").toBoolean
  }

  case class StravaAuthResult(code: String, token: String, refreshToken: String, refreshExpire: Long, mapboxToken: String, id: String, name: String, sessionId: String) {
    // userId used for serialization, needs to be stable, cannot be created from a token
    lazy val userId: String = id
  }

  private def buildAuthJson: util.HashMap[String, String] = {
    val json = new util.HashMap[String, String]()
    val SecretResult(clientId, clientSecret, mapboxToken, _, _) = secret

    json.put("client_id", clientId)
    json.put("client_secret", clientSecret)
    json
  }

  private def authRequest(json: util.HashMap[String, String]): JsonNode = {
    val content = new JsonHttpContent(new JacksonFactory(), json)

    val request = requestFactory.buildPostRequest(new GenericUrl("https://www.strava.com/oauth/token"), content)
    val response = request.execute() // TODO: async?

    jsonMapper.readTree(response.getContent)
  }

  def stravaAuth(code: String): StravaAuthResult = {

    val SecretResult(clientId, clientSecret, mapboxToken, _, _) = secret

    val json = buildAuthJson
    json.put("code", code)
    json.put("grant_type", "authorization_code")

    val responseJson = authRequest(json)

    val token = responseJson.path("access_token").textValue
    val refreshToken = responseJson.path("refresh_token").textValue
    val refreshExpire = responseJson.path("expires_at").longValue

    val athleteJson = responseJson.path("athlete")
    val id = athleteJson.path("id").numberValue.toString
    val name = athleteJson.path("firstname").textValue + " " + athleteJson.path("lastname").textValue

    val sessionId = "full-session-" + System.currentTimeMillis().toString
    val auth = StravaAuthResult(code, token, refreshToken, refreshExpire, mapboxToken, id, name, sessionId)
    rest.RestAPIServer.createUser(auth)
    auth
  }

  def stravaAuthRefresh(previous: StravaAuthResult): StravaAuthResult = {
    // if not expired yet (with some space left), use it
    // if expired or about to expire soon, request a new one
    // https://developers.strava.com/docs/authentication/:
    //  If the application’s access tokens for the user are expired or will expire in one hour (3,600 seconds) or less, a new access token will be returned
    val now = System.currentTimeMillis / 1000
    val validUntil = previous.refreshExpire - 3600
    if (now > validUntil) {
      val json = buildAuthJson
      json.put("refresh_token", previous.refreshToken)
      json.put("grant_type", "refresh_token")

      val responseJson = authRequest(json)

      val token = responseJson.path("access_token").textValue
      val refreshToken = responseJson.path("refresh_token").textValue
      val refreshExpire = responseJson.path("expires_at").longValue

      val auth = previous.copy(token = token, refreshToken = refreshToken, refreshExpire = refreshExpire)
      rest.RestAPIServer.createUser(auth)
      auth
    } else {
      previous
    }
  }


  def loadActivityId(json: JsonNode): ActivityHeader = {
    // https://developers.strava.com/docs/reference/#api-Activities-getActivityById
    val name = json.path("name").textValue
    val id = json.path("id").longValue
    val time = ZonedDateTime.parse(json.path("start_date").textValue)
    val sportName = json.path("type").textValue
    val duration = json.path("elapsed_time").intValue
    val distance = json.path("distance").doubleValue
    val hasGPS = !json.path("start_latlng").isMissingNode && !json.path("start_latlng").isNull
    val hasHR = json.path("has_heartrate").booleanValue
    val avgSpeed = json.path("average_speed").doubleValue
    val maxSpeed = json.path("max_speed").doubleValue
    val actDigest = digest(json.toString)

    def sportFromName(name: String): Event.Sport = {
      try {
        Event.Sport.withName(sportName)
      } catch {
        case _: NoSuchElementException => SportId.Workout
      }
    }

    val actId = ActivityId(FileId.StravaId(id), actDigest, name, time, time.plusSeconds(duration), sportFromName(sportName), distance)
    ActivityHeader(actId,hasGPS,hasHR,SpeedStats(avgSpeed, avgSpeed, maxSpeed))
  }

  def parseStravaActivities(content: InputStream): Seq[ActivityHeader] = {
    val responseJson = jsonMapper.readTree(content)

    val stravaActivities = (0 until responseJson.size).map { i =>
      val actI = responseJson.get(i)
      loadActivityId(actI)
    }
    stravaActivities
  }

  def lastStravaActivities(auth: StravaAuthResult, count: Int): Seq[ActivityId] = {
    val timing = Timing.start()
    val uri = "https://www.strava.com/api/v3/athlete/activities"
    val request = buildGetRequest(uri, auth.token, s"per_page=$count")

    val ret = parseStravaActivities(request.execute().getContent).map(_.id)
    timing.logTime(s"lastStravaActivities ($count)")
    ret
  }

  private val normalCount = 15

  def recentStravaActivities(auth: StravaAuthResult): Seq[ActivityId] = {
    lastStravaActivities(auth, normalCount)
  }

  def recentStravaActivitiesHistory(auth: StravaAuthResult, countMultiplier: Double = 1): (Seq[ActivityId], Seq[ActivityId]) = {
    val allActivities = lastStravaActivities(auth, (normalCount * countMultiplier).toInt)
    allActivities.splitAt(normalCount)
  }


  def stravaActivitiesNotStaged(auth: StravaAuthResult): Seq[ActivityId] = {
    val stravaActivities = recentStravaActivities(auth)
    if (stravaActivities.nonEmpty) {
      val notBefore = stravaActivities.map(_.startTime).min
      val storedActivities = stagedActivities(auth, notBefore)
      // do not display the activities which are already staged
      stravaActivities diff storedActivities
    } else {
      stravaActivities
    }
  }

  object namespace {
    // stage are data visible to the user
    val stage = "stage"
    // editable data - not listed in staging
    val edit = "edit"
    // session storage
    def session(session: String) = "session/" + session
    // file upload progress
    val uploadProgress = "upload-progress"
    // upload - invisible data, used to hand data to the background upload tasks
    def upload(session: String) = "upload-" + session
    // upload results - report upload status and resulting id
    def uploadResult(session: String) = "upload-result-" + session
    // upload results - report upload status and resulting id
    def pushProgress(session: String) = "push-pending-" + session
    // user settings
    val settings = "settings"
  }

  def stagedActivities(auth: StravaAuthResult, notBefore: ZonedDateTime): Seq[ActivityHeader] = {
    val storedActivities = {
      def isNotBeforeByName(name: String) = {
        val md = Storage.metadataFromFilename(name)
        md.get("startTime").forall(timeString => ZonedDateTime.parse(timeString) >= notBefore)
      }
      val d = Storage.enumerate(namespace.stage, auth.userId, Some(isNotBeforeByName))
      d.flatMap { a =>
        Storage.load[ActivityHeader](a._1)
      }
    }
    storedActivities.toVector
  }

  private def segmentTitle(kind: String, e: SegmentTitle): String = {
    val segPrefix = if (e.isPrivate) "private segment " else "segment "
    val segmentName = Main.shortNameString(e.name, 32 - segPrefix.length - kind.length)
    val complete = if (e.segmentId != 0) {
      kind + segPrefix + <a title={e.name} href={s"https://www.strava.com/segments/${e.segmentId}"}>{segmentName}</a>
    } else {
      kind + segPrefix + segmentName
    }
    complete.capitalize
  }

  def htmlDescription(event: Event): String = event match {
    case e: PauseEvent =>
      s"Pause ${Events.niceDuration(e.duration)}"
    case e: PauseEndEvent =>
      "Pause end"
    case e: LapEvent =>
      "Lap"
    case e: EndEvent =>
      "End"
    case e: BegEvent =>
      "<b>Start</b>"
    case e: SplitEvent =>
      "Split"
    case e: StartSegEvent =>
      segmentTitle("", e)
    case e: EndSegEvent =>
      segmentTitle("end ", e)
    case e: ElevationEvent =>
      Main.shortNameString("Elevation " + e.elev.toInt + " m")
  }

  @SerialVersionUID(10L)
  case object NoActivity

  object ActivityEvents {
    def mergeAttributes(thisAttributes: Seq[DataStreamAttrib], thatAttributes: Seq[DataStreamAttrib]): Seq[DataStreamAttrib] = {
      val mergedAttr = thisAttributes.map { a =>
        val aThat = thatAttributes.find(_.streamType == a.streamType)
        val aStream = aThat.map(a.stream ++ _.stream).getOrElse(a.stream)
        a.pickData(aStream.asInstanceOf[a.DataMap])
      }
      val notMergedFromThat = thatAttributes.find(ta => !thisAttributes.exists(_.streamType == ta.streamType))
      mergedAttr ++ notMergedFromThat
    }
  }

  @SerialVersionUID(10L)
  case class ActivityEvents(id: ActivityId, events: Array[Event], dist: DataStreamDist, gps: DataStreamGPS, attributes: Seq[DataStreamAttrib]) {
    self =>



    import ActivityEvents._

    def computeDistStream = {
      if (gps.stream.nonEmpty) {
        gps.distStream
      } else {
        DataStreamGPS.distStreamFromRouteStream(dist.stream.toSeq)
      }
    }

    def computeSpeedStats: SpeedStats = DataStreamGPS.speedStats(DataStreamGPS.computeSpeedStream(computeDistStream))

    def header: ActivityHeader = ActivityHeader(id, hasGPS, hasAttributes, computeSpeedStats)

    def streams: Seq[DataStream] = {
      Seq(dist, gps).filter(_.nonEmpty) ++ attributes
    }

    def startTime = id.startTime
    def endTime = id.endTime
    def duration: Double = timeDifference(startTime, endTime)

    def isAlmostEmpty(minDurationSec: Int) = {
      val ss = streams
      !ss.exists(_.stream.nonEmpty) || endTime < startTime.plusSeconds(minDurationSec) || ss.exists(x => x.isAlmostEmpty)
    }

    override def toString = id.toString
    def toLog: String = streams.map(_.toLog).mkString(", ")

    assert(events.forall(_.stamp >= id.startTime))
    assert(events.forall(_.stamp <= id.endTime))

    assert(events.forall(_.stamp <= id.endTime))

    assert(gps.inTimeRange(id.startTime, id.endTime))
    assert(dist.inTimeRange(id.startTime, id.endTime))
    assert(attributes.forall(_.inTimeRange(id.startTime, id.endTime)))

    def secondsInActivity(time: ZonedDateTime): Int  = id.secondsInActivity(time)
    def timeInActivity(seconds: Int) = id.timeInActivity(seconds)

    private def convertGPSToPair(gps: GPSPoint) = (gps.latitude, gps.longitude)

    def begPos: (Double, Double) = convertGPSToPair(gps.stream.head._2)
    def endPos: (Double, Double) = convertGPSToPair(gps.stream.last._2)

    // must call hasGPS because it is called while composing the JS, even when hasGPS is false
    def lat: Double = if (hasGPS) (begPos._1 + endPos._1) * 0.5 else 0.0
    def lon: Double = if (hasGPS) (begPos._2 + endPos._2) * 0.5 else 0.0

    def hasGPS: Boolean = gps.nonEmpty
    def hasAttributes: Boolean = attributes.exists(_.stream.nonEmpty)

    def distanceForTime(time: ZonedDateTime): Double = dist.distanceForTime(time)

    lazy val elevation: Double = {
      val elevationStream = gps.stream.flatMap {
        case (k, v) =>
          v.elevation.map(k -> _.toDouble)
      }
      val elevations = elevationStream.values
      (elevations zip elevations.drop(1)).map {case (prev, next) => (next - prev) max 0}.sum
    }

    def eventTimes: DataStream.EventTimes = events.map(_.stamp)(collection.breakOut)
    def optimize: ActivityEvents = {
      // first optimize all attributes
      val times = eventTimes
      this.copy(gps = gps.optimize(times), dist = dist.optimize(times), attributes = attributes.map(_.optimize(times)))
    }

    def optimizeRouteForMap: Seq[(ZonedDateTime, GPSPoint)] = {
      val maxPoints = 3000
      if (gps.stream.size < maxPoints) gps.stream.toList
      else {
        // first apply generic GPS optimization
        val data = gps.optimize(eventTimes)

        if (data.stream.size < maxPoints) data.stream.toList
        else {
          val ratio = (data.stream.size / maxPoints.toDouble).ceil.toInt
          val gpsSeq = data.stream.toList

          val groups = gpsSeq.grouped(ratio).toList

          // take each n-th
          val allButLast = groups.dropRight(1).map(_.head)
          // always take the last one
          val lastGroup = groups.last
          val last = if (lastGroup.lengthCompare(1) > 0) lastGroup.take(1) ++ lastGroup.takeRight(1)
          else lastGroup

          allButLast ++ last
        }
      }
    }

    def routeJS: String = {
      val toSend = optimizeRouteForMap

      toSend.map { case (time, g) =>
        val t = id.secondsInActivity(time)
        val d = distanceForTime(time)
        s"[${g.longitude},${g.latitude},$t,$d]"
      }.mkString("[\n", ",\n", "]\n")
    }

    def routeData: Seq[(Double, Double, Double, Double)] = {
      val toSend = optimizeRouteForMap
      toSend.map { case (time, g) =>
        val t = id.secondsInActivity(time)
        val d = distanceForTime(time)
        (g.longitude, g.latitude, t.toDouble, d)
      }
    }

    def merge(that: ActivityEvents): ActivityEvents = {
      // select some id (name, sport ...)
      val begTime = Seq(id.startTime, that.id.startTime).min
      val endTime = Seq(id.endTime, that.id.endTime).max

      // TODO: unique ID (merge or hash input ids?)
      val sportName = if (Event.sportPriority(id.sportName) < Event.sportPriority(that.id.sportName)) id.sportName else that.id.sportName

      val eventsAndSports = (events ++ that.events).sortBy(_.stamp)

      // keep only first start Event, change other to Split only
      val (begs, others) = eventsAndSports.partition(_.isInstanceOf[BegEvent])
      val (ends, rest) = others.partition(_.isInstanceOf[EndEvent])

      val begsSorted = begs.sortBy(_.stamp).map(_.asInstanceOf[BegEvent])
      val begsAdjusted = begsSorted.take(1) ++ begsSorted.drop(1).map(e => SplitEvent(e.stamp, e.sport))

      // when activities follow each other, insert a lap or a pause between them
      val begsEnds = (begs ++ ends).sortBy(_.stamp)

      val pairs = begsEnds zip begsEnds.drop(1)
      val transitionEvents: Seq[Event] = pairs.flatMap {
        case (e: EndEvent, b: BegEvent) =>
          val duration = timeDifference(e.stamp, b.stamp).toInt
          if (duration < 60) {
            Seq(LapEvent(e.stamp), LapEvent(b.stamp))
          } else {
            Seq(PauseEvent(duration, e.stamp), PauseEndEvent(duration, b.stamp))
          }
        case _ =>
          Seq.empty
      }

      val eventsAndSportsSorted = (begsAdjusted ++ rest ++ transitionEvents :+ ends.maxBy(_.stamp) ).sortBy(_.stamp)

      val startBegTimes = Seq(this.startTime, this.endTime, that.startTime, that.endTime).sorted

      val timeIntervals = startBegTimes zip startBegTimes.tail

      val streams = for (timeRange <- timeIntervals) yield {
        // do not merge overlapping distances, prefer distance from a GPS source
        val thisGpsPart = this.gps.slice(timeRange._1, timeRange._2)
        val thatGpsPart = that.gps.slice(timeRange._1, timeRange._2)

        val thisDistPart = this.dist.slice(timeRange._1, timeRange._2)
        val thatDistPart = that.dist.slice(timeRange._1, timeRange._2)

        val thisAttrPart = this.attributes.map(_.slice(timeRange._1, timeRange._2))
        val thatAttrPart = that.attributes.map(_.slice(timeRange._1, timeRange._2))

        (
          if (thisGpsPart.stream.size > thatGpsPart.stream.size) thisGpsPart else thatGpsPart,
          if (thisDistPart.stream.size > thatDistPart.stream.size) thisDistPart else thatDistPart,
          // assume we can use attributes from both sources, do not prefer one over another
          mergeAttributes(thisAttrPart, thatAttrPart)
        )
      }

      // distance streams need offsetting
      // when some part missing a distance stream, we need to compute the offset from GPS

      var offset = 0.0
      val offsetStreams = for ((gps, dist, attr) <- streams) yield {
        val partDist = dist.stream.lastOption.fold(gps.distStream.lastOption.fold(0.0)(_._2))(_._2)
        val startOffset = offset
        offset += partDist
        (gps.stream, dist.offsetDist(startOffset).stream, attr)
      }

      val totals = offsetStreams.fold(offsetStreams.head) { case ((totGps, totDist, totAttr), (iGps, iDist, iAttr)) =>
        (totGps ++ iGps, totDist ++ iDist, mergeAttributes(totAttr, iAttr))
      }
      val mergedId = ActivityId(FileId.TempId(id.id.filename), "", id.name, begTime, endTime, sportName, dist.stream.last._2)

      ActivityEvents(mergedId, eventsAndSportsSorted, dist.pickData(totals._2), gps.pickData(totals._1), totals._3).unifySamples
    }

    def editableEvents: Array[EditableEvent] = {

      val ees = events.map { e =>
        val action = e.defaultEvent
        EditableEvent(action, id.secondsInActivity(e.stamp), distanceForTime(e.stamp), e.listTypes, e.originalEvent, htmlDescription(e))
      }

      // consolidate mutliple events with the same time so that all of them have the same action
      val merged = ees.groupBy(_.time).map { case (t, es) =>
        object CmpEvent extends Ordering[String] {
          def compare(x: String, y: String): Int = {
            def score(et: String) = {
              if (et == "lap") 1
              else if (et.startsWith("split")) 2
              else if (et == "end") -1
              else 0
            }
            score(x) - score(y)
          }
        }
        (t, es.map(_.action).max(CmpEvent))
      }

      ees.map { e => e.copy(action = merged(e.time))}

    }

    def split(splitTime: ZonedDateTime): Option[ActivityEvents] = {
      val logging = false

      if (logging) println(s"Split ${id.startTime}..${id.endTime} at $splitTime")

      // we always want to keep the splitTime even if it is not a split event. This happens when deleting part of activities
      // because some split times are suppressed during the process
      val splitEvents = events.filter(e => e.isSplit || e.stamp == splitTime).toSeq

      val splitTimes = splitEvents.map(e => e.stamp)

      assert(splitTimes.contains(id.startTime))
      assert(splitTimes.contains(id.endTime))

      val splitRanges = splitEvents zip splitTimes.tail

      val toSplit = splitRanges.find(_._1.stamp == splitTime)

      toSplit.map { case (beg, endTime) =>

        val begTime = beg.stamp
        if (logging) println(s"keep $begTime..$endTime")

        val eventsRange = events.dropWhile(_.stamp <= begTime).takeWhile(_.stamp < endTime)

        val distRange = dist.pickData(dist.slice(begTime, endTime).stream)
        val gpsRange = gps.pickData(gps.slice(begTime, endTime).stream)

        val attrRange = attributes.map { attr =>
          attr.slice(begTime, endTime)
        }

        val sport = beg.sportChange.getOrElse(id.sportName)

        val act = ActivityEvents(id.copy(startTime = begTime, endTime = endTime, sportName = sport), eventsRange, distRange, gpsRange, attrRange)

        act
      }
    }

    def span(time: ZonedDateTime): (Option[ActivityEvents], Option[ActivityEvents]) = {

      val (takeDist, leftDist) = dist.span(time)
      val (takeGps, leftGps) = gps.span(time)
      val splitAttributes = attributes.map(_.span(time))

      val takeAttributes = splitAttributes.map(_._1)
      val leftAttributes = splitAttributes.map(_._2)

      val (takeEvents, leftEvents) = events.span(_.stamp < time)

      val (takeBegTime, takeEndTime) = (startTime, time)

      val (leftBegTime, leftEndTime) = (time, endTime)

      val takeMove = if (takeBegTime < takeEndTime) {
        Some(ActivityEvents(id.copy(startTime = takeBegTime, endTime = takeEndTime), takeEvents, takeDist, takeGps, takeAttributes))
      } else None
      val leftMove = if (leftBegTime < leftEndTime) {
        Some(ActivityEvents(id.copy(startTime = leftBegTime, endTime = leftEndTime), leftEvents, leftDist, leftGps, leftAttributes))
      } else None

      (takeMove, leftMove)
    }

    def timeOffset(offset: Int): ActivityEvents = {
      copy(
        id = id.timeOffset(offset),
        events = events.map(_.timeOffset(offset)),
        gps = gps.timeOffset(offset),
        dist = dist.timeOffset(offset),
        attributes = attributes.map(_.timeOffset(offset)))
    }

    def processPausesAndEvents: ActivityEvents = {
      val timing = Timing.start()
      //val cleanLaps = laps.filter(l => l > actId.startTime && l < actId.endTime)

      // prefer GPS, as this is already cleaned for accuracy error
      val distStream = if (this.gps.isEmpty) {
        DataStreamGPS.distStreamFromRouteStream(this.dist.stream.toSeq)
      } else {
        this.gps.distStream
      }

      timing.logTime("distStream")

      val speedStream = DataStreamGPS.computeSpeedStream(distStream)
      val speedMap = speedStream

      // integrate route distance back from smoothed speed stream so that we are processing consistent data
      val routeDistance = DataStreamGPS.routeStreamFromSpeedStream(speedStream)

      timing.logTime("routeDistance")

      // find pause candidates: times when smoothed speed is very low
      val speedPauseMax = 0.7
      val speedPauseAvg = 0.4
      val minPause = 10 // minimal pause to record
      val minLongPause = 20 // minimal pause to introduce end pause event
      val minSportChangePause = 50  // minimal pause to introduce automatic transition between sports
      val minSportDuration = 15 * 60 // do not change sport too often, assume at least 15 minutes of activity

      // select samples which are slow and the following is also slow (can be in the middle of the pause)
      type PauseStream = List[(ZonedDateTime, ZonedDateTime, Double)]
      val pauseSpeeds: PauseStream = (speedStream zip speedStream.drop(1)).collect {
        case ((t1, _), (t2, s)) if s < speedPauseMax => (t1, t2, s)
      }.toList
      // aggregate pause intervals - merge all
      def mergePauses(pauses: PauseStream, done: PauseStream): PauseStream = {
        pauses match {
          case head :: next :: tail =>
            if (head._2 == next._1) { // extend head with next and repeat
              mergePauses(head.copy(_2 = next._2) :: tail, done)
            } else { // head can no longer be extended, use it, continue processing
              mergePauses(next +: tail, head +: done)
            }
          case _ => pauses ++ done
        }
      }

      val mergedPauses = mergePauses(pauseSpeeds, Nil).reverse

      timing.logTime("mergePauses")

      def avgSpeedDuring(beg: ZonedDateTime, end: ZonedDateTime): Double = {
        val findBeg = routeDistance.to(beg).lastOption
        val findEnd = routeDistance.from(end).headOption
        val avgSpeed = for (b <- findBeg; e <- findEnd) yield {
          val duration = ChronoUnit.SECONDS.between(b._1, e._1)
          if (duration > 0) (e._2 - b._2) / duration else 0
        }
        avgSpeed.getOrElse(0)
      }

      type Pause = (ZonedDateTime, ZonedDateTime)
      def pauseDuration(p: Pause) = timeDifference(p._1, p._2)

      // take a pause candidate and reduce its size until we get a real pause (or nothing)
      def extractPause(beg: ZonedDateTime, end: ZonedDateTime): List[Pause] = {

        val pauseArea = speedStream.from(beg).to(end)

        // locate a point which is under required avg speed, this is guaranteed to serve as a possible pause center
        val (_, candidateStart) = pauseArea.span(_._2 > speedPauseAvg)
        val (candidate, _) = candidateStart.span(_._2 <= speedPauseAvg)
        // now take all under the speed

        def isPauseDuring(b: ZonedDateTime, e: ZonedDateTime, rect: DataStreamGPS.GPSRect) = {
          val gpsRange = gps.stream.from(b).to(e)

          val extendRect = for {
            gpsBeg <- gpsRange.headOption
            gpsEnd <- gpsRange.lastOption
          } yield {
            rect.merge(gpsBeg._2).merge(gpsEnd._2)
          }
          val extendedRect = extendRect.getOrElse(rect)
          val rectSize = extendedRect.size
          val rectDuration = ChronoUnit.SECONDS.between(b, e)
          val rectSpeed = if (rectDuration > 0) rectSize / rectDuration else 0
          // until the pause is long enough, do not evaluate its speed
          (rectSpeed < speedPauseAvg || rectDuration < minPause, extendedRect)
        }

        def extendPause(b: ZonedDateTime, e: ZonedDateTime, rect: DataStreamGPS.GPSRect): Pause = {
          // try extending beg first
          // b .. e is inclusive
          val prevB = pauseArea.to(b).dropRight(1).lastOption.map(_._1)
          val nextE = pauseArea.from(e).drop(1).headOption.map(_._1)

          val pauseB = prevB.map(isPauseDuring(_, e, rect))
          val pauseE = nextE.map(isPauseDuring(b, _, rect))
          if (pauseB.isDefined && pauseB.get._1) {
            extendPause(prevB.get, e, pauseB.get._2)
          } else if (pauseE.isDefined && pauseE.get._1) {
            extendPause(b, nextE.get, pauseE.get._2)
          } else {
            (beg, end)
          }
        }

        val candidateRange = for {
          b <- candidate.headOption
          e <- candidate.lastOption
        } yield {
          (b._1, e._1)
        }

        val candidatePause = candidateRange.toList.flatMap { case (cb, ce) =>
          val gpsRange = gps.stream.from(cb).to(ce)
          val gpsRect = gpsRange.foldLeft(new DataStreamGPS.GPSRect(gpsRange.head._2))((rect, p) => rect merge p._2)
          val cp = extendPause(cb, ce, gpsRect)
          // skip the extended pause
          val next = pauseArea.from(cp._2).drop(1).headOption
          next.map(n => cp :: extractPause(n._1, end)).getOrElse(List(cp))
        }
        candidatePause
      }

      def cleanPauses(ps: List[Pause]): List[Pause] = {
        // when pauses are too close to each other, delete them or merge them
        def recurse(todo: List[Pause], done: List[Pause]): List[Pause] = {
          def shouldBeMerged(first: (ZonedDateTime, ZonedDateTime), second: (ZonedDateTime, ZonedDateTime)) = {
            timeDifference(first._2, second._1) < 120 && avgSpeedDuring(first._2, second._1) < 2
          }

          def shouldBeDiscardedFirst(first: (ZonedDateTime, ZonedDateTime), second: (ZonedDateTime, ZonedDateTime)) = {
            timeDifference(first._2, second._1) < 240
          }

          todo match {
            case first :: second :: tail if shouldBeMerged(first, second) =>
              recurse((first._1, second._2) :: tail, done)
            case first :: second :: tail if shouldBeDiscardedFirst(first, second) =>
              val longer = Seq(first, second).maxBy(pauseDuration)
              recurse(longer :: tail, done)
            case head :: tail =>
              recurse(tail, head :: done)
            case _ =>
              done
          }
        }
        var cleaned = recurse(ps, Nil).reverse

        // if there are too many pauses, remove the shortest ones
        breakable {
          while (cleaned.nonEmpty) {
            // find shortest pause
            // 10 pauses: keep only pauses above 100 seconds
            val limit = cleaned.size * 10

            val minPause = cleaned.minBy(pauseDuration)

            if (pauseDuration(minPause) > limit) break

            cleaned = cleaned.patch(cleaned.indexOf(minPause), Nil, 1)
          }
        }
        cleaned
      }

      val extractedPauses = mergedPauses.flatMap(p => extractPause(p._1, p._2))

      timing.logTime("extractedPauses")

      val cleanedPauses = cleanPauses(extractedPauses)

      val pauseEvents = cleanedPauses.flatMap { case (tBeg, tEnd) =>
        val duration = ChronoUnit.SECONDS.between(tBeg, tEnd).toInt
        if (duration > minLongPause) {
          Seq(PauseEvent(duration, tBeg), PauseEndEvent(duration, tEnd))
        } else if (duration > minPause) {
          Seq(PauseEvent(duration, tBeg))
        } else Seq()
      }

      def collectSportChanges(todo: List[Pause], done: List[Pause]): List[Pause] = {
        todo match {
          case first :: second :: tail if timeDifference(first._2, second._1) < minSportDuration =>
            val longer = Seq(first, second).maxBy(pauseDuration)
            collectSportChanges(longer :: tail, done)
          case head :: tail if timeDifference(head._1, head._2) > minSportChangePause =>
            collectSportChanges(tail, head :: done)
          case _ :: tail =>
            collectSportChanges(tail, done)
          case _ =>
            done
        }
      }

      val sportChangePauses = collectSportChanges(cleanedPauses, Nil).reverse

      val sportChangeTimes = sportChangePauses.flatMap(p => Seq(p._1, p._2))

      val intervalTimes = (id.startTime +: sportChangeTimes :+ id.endTime).distinct

      def speedDuringInterval(beg: ZonedDateTime, end: ZonedDateTime) = {
        speedMap.from(beg).to(end)
      }

      def intervalTooShort(beg: ZonedDateTime, end: ZonedDateTime) = {
        val duration = ChronoUnit.SECONDS.between(beg, end)
        val distance = avgSpeedDuring(beg, end) * duration
        duration < 60 && distance < 100
      }

      val intervals = intervalTimes zip intervalTimes.drop(1)

      val sportsInRanges = intervals.flatMap { case (pBeg, pEnd) =>

        assert(pEnd > pBeg)
        if (sportChangePauses.exists(_._1 == pBeg) || intervalTooShort(pBeg, pEnd)) {
          None // no sport detection during pauses (would always detect as something slow, like Run
        } else {

          val spd = speedDuringInterval(pBeg, pEnd)

          val speedStats = DataStreamGPS.speedStats(spd)

          val sport = detectSportBySpeed(speedStats, id.sportName)

          Some(pBeg, sport)
        }
      }

      // reversed, as we will be searching for last lower than
      val sportsByTime = sportsInRanges.sortBy(_._1)(Ordering[ZonedDateTime].reverse)

      def findSport(time: ZonedDateTime) = {
        sportsByTime.find(_._1 <= time).map(_._2).getOrElse(id.sportName)
      }

      // process existing events
      val inheritEvents = this.events.filterNot(_.isSplit)

      val hillEvents = findHills(gps, distStream)

      val events = (BegEvent(id.startTime, findSport(id.startTime)) +: EndEvent(id.endTime) +: inheritEvents) ++ pauseEvents ++ hillEvents
      val eventsByTime = events.sortBy(_.stamp)

      val sports = eventsByTime.map(x => findSport(x.stamp))

      // insert / modify splits on edges
      val sportChange = (("" +: sports) zip sports).map(ab => ab._1 != ab._2)
      val allEvents = (eventsByTime, sports, sportChange).zipped.map { case (ev, sport,change) =>
        // TODO: handle multiple events at the same time
        if (change) {
          if (ev.isInstanceOf[BegEvent]) BegEvent(ev.stamp, sport)
          else SplitEvent(ev.stamp, sport)
        }
        else ev
      }

      // when there are multiple events at the same time, use only the most important one
      @tailrec
      def cleanupEvents(es: List[Event], ret: List[Event]): List[Event] = {
        es match {
          case first :: second :: tail if first.stamp == second.stamp =>
            if (first.order < second.order) cleanupEvents(first :: tail, ret)
            else cleanupEvents(second :: tail, ret)
          case head :: tail =>
            cleanupEvents(tail, head :: ret)
          case _ =>
            ret
        }
      }

      val cleanedEvents = cleanupEvents(allEvents.sortBy(_.stamp).toList, Nil).reverse

      timing.logTime("extractPause done")

      copy(events = cleanedEvents.toArray)
    }


    /*
    Clean errors in buildings and other areas where signal is bad and position error high
    (EHPE - estimated horizontal positition error)
    * */
    def cleanGPSPositionErrors: ActivityEvents = {

      def vecFromGPS(g: GPSPoint) = Vector2(g.latitude, g.longitude)
      //def gpsFromVec(v: Vector2) = GPSPoint(latitude = v.x, longitude = v.y, None)(None)

      @tailrec
      def cleanGPS(todoGPS: List[gps.ItemWithTime], done: List[gps.ItemWithTime]): List[gps.ItemWithTime] = {
        todoGPS match {
          case first :: second :: tail if second._2.accuracy > 8 =>
            // move second as little as possible to stay within GPS accuracy error
            val gps1 = first._2
            val gps2 = second._2
            val v1 = vecFromGPS(gps1)
            val v2 = vecFromGPS(gps2)

            val maxDist = second._2.accuracy * 2 // * 2 is empirical, tested activity looks good with this value
            val dist = gps1 distance gps2
            // move as far from v2 (as close to v1) as accuracy allows
            if (dist > maxDist) {
              val clamped = (v1 - v2) * (maxDist / dist) + v2
              val gpsClamped = gps1.copy(clamped.x, clamped.y)(None)
              cleanGPS(second.copy(_2 = gpsClamped) :: tail, first :: done)
            } else {
              cleanGPS(second.copy(_2 = first._2) :: tail, first :: done)
            }
          case head :: tail =>
            cleanGPS(tail, head :: done)
          case _ =>
            done
        }
      }

      val gpsClean = cleanGPS(gps.stream.toList, Nil).reverse
      val gpsStream = gps.pickData(SortedMap(gpsClean:_*))

      // rebuild dist stream as well

      // TODO: DRY
      val distanceDeltas = gpsStream.distStream
      val distances = DataStreamGPS.routeStreamFromDistStream(distanceDeltas.toSeq)

      copy(gps = gpsStream, dist = dist.pickData(distances))

    }

    def cleanPositionErrors: ActivityEvents = {
      if (hasGPS) cleanGPSPositionErrors
      else this
    }

    /// input filters - elevation filtering, add temperature info
    def applyFilters(auth: StravaAuthResult): ActivityEvents = {
      val settings = Settings(auth.userId)
      val useElevFilter = id.id match {
        case _: FileId.StravaId =>
          false
        case _ =>
          true
      }
      val elevFiltered = if (useElevFilter) copy(gps = gps.filterElevation(Settings(auth.userId).elevFilter)) else this
      val hrFiltered = elevFiltered.attributes.map {
        case hr: DataStreamHR =>
          hr.removeAboveMax(settings.maxHR)
        case attr =>
          attr
      }
      if (attributes.exists(_.attribName == "temp")) {
        copy(attributes = hrFiltered)
      } else {
        val temperaturePos = weather.GetTemperature.pickPositions(elevFiltered.gps)
        if (temperaturePos.nonEmpty) {
          val temperature = weather.GetTemperature.forPositions(temperaturePos)
          copy(attributes = temperature +: hrFiltered)
        } else {
          copy(attributes = hrFiltered)
        }
      }
    }


    /// output filters - swim data cleanup
    def applyUploadFilters(auth: StravaAuthResult): ActivityEvents = {
      id.sportName match {
        case Event.Sport.Swim if gps.nonEmpty =>
          swimFilter
        case _ =>
          this
      }
    }

    // swim filter - avoid large discrete steps which are often found in swim sparse data
    def swimFilter: ActivityEvents = {

      @tailrec
      def handleInaccuratePartsRecursive(stream: DataStreamGPS.GPSStream, done: DataStreamGPS.GPSStream): DataStreamGPS.GPSStream = {

        def isAccurate(p: (ZonedDateTime, GPSPoint)) = p._2.in_accuracy.exists(_ < 8)

        val (prefix, temp) = stream.span(isAccurate)
        val (handleInner, rest) = temp.span(x => !isAccurate(x))

        if (handleInner.isEmpty) {
          assert(rest.isEmpty)
          done ++ stream
        } else {
          val start = prefix.lastOption orElse done.lastOption // prefer last accurate point if available
          val end = rest.headOption // prefer first accurate point if available
          val handle = handleInner ++ start ++ end
          val duration = timeDifference(handle.head._1, handle.last._1)

          val gpsDistances = DataStreamGPS.routeStreamFromGPS(handle)
          val totalDist = gpsDistances.last._2

          // build gps position by distance curve
          val gpsByDistance = SortedMap((gpsDistances.values zip handle.values).toSeq: _*)

          // found gps data for given distance
          def gpsWithDistance(d: Double): GPSPoint = {
            val get = for {
              prev <- gpsByDistance.to(d).lastOption
              next <- gpsByDistance.from(d).headOption
            } yield {
              def vecFromGPS(g: GPSPoint) = Vector2(g.latitude, g.longitude)

              def gpsFromVec(v: Vector2) = GPSPoint(latitude = v.x, longitude = v.y, None)(None)

              val f = if (next._1 > prev._1) (d - prev._1) / (next._1 - prev._1) else 0
              val p = vecFromGPS(prev._2)
              val n = vecFromGPS(next._2)
              gpsFromVec((n - p) * f + p)
            }
            get.get
          }

          val gpsSwim = for (time <- 0 to duration.toInt) yield {
            val d = (time * totalDist / duration) min totalDist // avoid rounding errors overflowing end of the range
            val t = handle.firstKey.plusSeconds(time)
            t -> gpsWithDistance(d)
          }
          handleInaccuratePartsRecursive(rest, done ++ prefix ++ gpsSwim)
        }

      }

      val gpsSwim = handleInaccuratePartsRecursive(gps.stream, SortedMap.empty)

      copy(gps = gps.pickData(gpsSwim))
    }

    def unifySamples: ActivityEvents = {
      // make sure all distance and attribute times are aligned with GPS times
      val times = gps.stream.keys.toList
      //dist
      val unifiedAttributes = attributes.map(a => a.samplesAt(times))
      copy(attributes = unifiedAttributes)
    }

    trait Stats {
      def distanceInM: Double
      def totalTimeInSeconds: Double
      def speed: Double
      def movingTime: Double
      def elevation: Double
    }
    def stats: Stats = new Stats {
      val distanceInM = id.distance
      val totalTimeInSeconds = duration
      val speed = distanceInM / totalTimeInSeconds
      val movingTime = 0.0
      val elevation = self.elevation
    }
  }

  trait ActivityStreams {
    def dist: DataStreamDist

    def latlng: DataStreamGPS

    def attributes: Seq[DataStreamAttrib]
  }

  def detectSportBySpeed(stats: SpeedStats, defaultName: Event.Sport) = {
    def detectSport(maxRun: Double, fastRun: Double, medianRun: Double): Event.Sport = {
      if (stats.median <= medianRun && stats.fast <= fastRun && stats.max <= maxRun) Event.Sport.Run
      else Event.Sport.Ride
    }

    def paceToKmh(pace: Double) = 60 / pace

    def kmh(speed: Double) = speed

    val sport = defaultName match {
      case Event.Sport.Run =>
        // marked as run, however if clearly contradicting evidence is found, make it a ride
        detectSport(paceToKmh(2), paceToKmh(2.5), paceToKmh(3)) // 2 - 3 min/km possible
      case Event.Sport.Ride =>
        detectSport(kmh(20), kmh(17), kmh(15)) // 25 - 18 km/h possible
      case Event.Sport.Workout =>
        detectSport(paceToKmh(3), paceToKmh(4), paceToKmh(4)) // 3 - 4 min/km possible
      case s => s
    }
    sport
  }

  def findHills(latlng: DataStreamGPS, dist: DataStreamDist#DataMap): Seq[Event] = {
    // find global min and max
    if (latlng.stream.isEmpty) {
      Seq.empty
    } else {
      val routeDist = DataStreamGPS.routeStreamFromDistStream(dist.toSeq)

      case class ElevDist(stamp: ZonedDateTime, elev: Int, dist: Double)
      val elevStream = latlng.stream.toList.flatMap { case (stamp, gps) =>
        gps.elevation.map(e => ElevDist(stamp, e, routeDist(stamp)))
      }
      val max = elevStream.maxBy(_.elev)
      val min = elevStream.minBy(_.elev)
      val minimalHillHeight = 5
      if (max.elev > min.elev + minimalHillHeight) {
        val globalOnly = false

        if (globalOnly) {
          Seq(
            ElevationEvent(max.elev, max.stamp),
            ElevationEvent(min.elev, min.stamp)
          )
        } else {

          // find all local extremes

          // get rid of monotonous rise/descends
          def removeMidSlopes(todo: List[ElevDist], done: List[ElevDist]): List[ElevDist] = {
            todo match {
              case a0 :: a1 :: a2 :: tail =>
                if (a0.elev <= a1.elev && a1.elev <= a2.elev || a0.elev >= a1.elev && a1.elev >= a2.elev) {
                  removeMidSlopes(a0 :: a2 :: tail, done)
                } else {
                  removeMidSlopes(a1 :: a2 :: tail, a0 :: done)
                }
              case _ =>
                done.reverse
            }
          }


          def filterSlopes(input: List[ElevDist]): List[ElevDist] = {

            var todo = input
            breakable {
              while (todo.lengthCompare(2) > 0) {
                // find min elevation difference
                // removing this never shortens slope

                val elevPairs = todo zip todo.drop(1).map(_.elev)
                val elevDiff = elevPairs.map { case (ed, elev) => ed.stamp -> (ed.elev - elev).abs }

                val minElevDiff = elevDiff.minBy(_._2)

                // the less samples we have, the more
                // with 2 samples we ignore 15 meters
                // with 10 samples we ignore 75 meters
                // with 20 samples we ignore 150 meters

                val neverIgnoreElevCoef = 7.5
                if (minElevDiff._2 > todo.length * neverIgnoreElevCoef) break

                val locate = todo.indexWhere(_.stamp == minElevDiff._1)

                todo = todo.patch(locate, Nil, 2)
              }

            }
            todo
          }

          val slopes = removeMidSlopes(elevStream, Nil)

          //val slopesElev = slopes.map(_.elev)

          //val totalElev = (slopesElev zip slopesElev.drop(1)).map { case (a,b) => (a-b).abs }.sum
          //val minMaxDiff = max.elev - min.elev

          val filteredSlopes = filterSlopes(slopes)

          filteredSlopes.map(x => ElevationEvent(x.elev, x.stamp))
        }
      } else {
        Seq.empty
      }
    }
  }

  def processActivityStream(actId: ActivityId, act: ActivityStreams, laps: Seq[ZonedDateTime], segments: Seq[Event]): ActivityEvents = {

    //println(s"Raw laps $laps")
    val cleanLaps = laps.filter(l => l > actId.startTime && l < actId.endTime)

    //println(s"Clean laps $cleanLaps")

    val events = (BegEvent(actId.startTime, actId.sportName) +: EndEvent(actId.endTime) +: cleanLaps.map(LapEvent)) ++ segments

    val eventsByTime = events.sortBy(_.stamp)

    ActivityEvents(actId, eventsByTime.toArray, act.dist, act.latlng, act.attributes)
  }

  def getEventsFrom(authToken: String, id: String): ActivityEvents = {

    println(s"Download from strava $id")
    val uri = s"https://www.strava.com/api/v3/activities/$id"
    val request = buildGetRequest(uri, authToken, "")

    val responseJson = jsonMapper.readTree(request.execute().getContent)

    val actId = loadActivityId(responseJson).id
    val startDateStr = responseJson.path("start_date").textValue
    val startTime = ZonedDateTime.parse(startDateStr)

    object StravaActivityStreams extends ActivityStreams {
      // https://strava.github.io/api/v3/streams/
      //private val allStreams = Seq("time", "latlng", "distance", "altitude", "velocity_smooth", "heartrate", "cadence", "watts", "temp", "moving", "grade_smooth")
      private val wantStreams = Seq("time", "latlng", "distance", "altitude", "heartrate", "cadence", "watts"/*, "temp"*/)

      private val streamTypes = wantStreams.mkString(",")

      private val uri = s"https://www.strava.com/api/v3/activities/$id/streams/$streamTypes"
      private val request = buildGetRequest(uri, authToken, "")

      private val response = request.execute().getContent

      private val responseJson = jsonMapper.readTree(response)

      val streams = responseJson.elements.asScala.toIterable

      def getData[T](stream: Stream[JsonNode], get: JsonNode => T): Vector[T] = {
        if (stream.isEmpty) Vector()
        else stream.head.path("data").asScala.map(get).toVector
      }

      def getDataByName[T](name: String, get: JsonNode => T): Vector[T] = {
        val stream = streams.filter(_.path("type").textValue == name).toStream
        getData(stream, get)
      }

      def getAttribByName(name: String): (String, Seq[Int]) = {
        (name, getDataByName(name, _.asInt))
      }

      private def loadGpsPair(gpsItem: JsonNode) = {
        val elements = gpsItem.elements
        val lat = elements.next.asDouble
        val lng = elements.next.asDouble
        GPSPoint(lat, lng, None)(None)
      }

      val timeRelValues = getDataByName("time", _.asInt)
      val distValues = getDataByName("distance", _.asDouble)
      val latlngValues = getDataByName("latlng", loadGpsPair)
      val altValues = getDataByName("altitude", _.asDouble)

      val latLngAltValues = if (altValues.isEmpty) latlngValues else {
        (latlngValues zip altValues).map { case (gps,alt) =>
            gps.copy(elevation = Some(alt.toInt))(Some(gps.accuracy))
        }
      }

      val timeValues = timeRelValues.map ( t => startTime.plusSeconds(t))

      val attributeValues: Seq[(String, Seq[Int])] = Seq(
        getAttribByName("cadence"),
        getAttribByName("watts"),
        getAttribByName("temp"),
        getAttribByName("heartrate")
      )

      val dist = new DataStreamDist(SortedMap(timeValues zip distValues:_*))
      val latlng = new DataStreamGPS(SortedMap(timeValues zip latLngAltValues:_*))
      val attributes =  attributeValues.filter(_._2.nonEmpty).flatMap { case (name, values) =>
          name match {
            case "heartrate" => Some(new DataStreamHR(SortedMap(timeValues zip values:_*)))
            case _ => Some(new DataStreamAttrib(name, SortedMap(timeValues zip values:_*)))
          }
      }

    }

    val laps = {


      val requestLaps = buildGetRequest(s"https://www.strava.com/api/v3/activities/$id/laps", authToken, "")

      val response = requestLaps.execute().getContent

      val lapsJson = jsonMapper.readTree(response)

      val lapTimes = (for (lap <- lapsJson.elements.asScala) yield {
        val lapTimeStr = lap.path("start_date").textValue
        ZonedDateTime.parse(lapTimeStr)
      }).toList


      lapTimes.filter(_ > actId.startTime)
    }

    val segments: Seq[Event] = {
      val segmentList = responseJson.path("segment_efforts").asScala.toList
      segmentList.flatMap { seg =>
        val segStartTime = ZonedDateTime.parse(seg.path("start_date").textValue)
        val segName = seg.path("name").textValue
        val segDuration = seg.path("elapsed_time").intValue
        val segPrivate = seg.path("segment").path("private").booleanValue
        val segmentId = seg.path("segment").path("id").longValue
        Seq(
          StartSegEvent(segName, segPrivate, segmentId, segStartTime),
          EndSegEvent(segName, segPrivate, segmentId, segStartTime.plusSeconds(segDuration))
        )
      }
    }


    processActivityStream(actId, StravaActivityStreams, laps, segments)

  }

  def adjustEvents(events: ActivityEvents, eventsInput: Seq[String]): ActivityEvents = {
    val ee = events.events zip eventsInput

    val lapsAndSplits: Array[Event] = ee.flatMap { case (e, ei) =>
      e match {
        case ev@EndEvent(_) => Some(ev)
        case _ =>
          if (ei.startsWith("split")) {
            val sportName = ei.substring("split".length)
            Some(SplitEvent(e.stamp, Event.Sport.withName(sportName)))
          } else ei match {
            case "lap" => Some(LapEvent(e.stamp))
            case "end" => Some(EndEvent(e.stamp))
            case _ => None
          }
      }
    }
    events.copy(events = lapsAndSplits)
  }

  def jsDateRange(startTime: ZonedDateTime, endTime: ZonedDateTime): String = {
    s"""formatDateTime("$startTime") + "..." + formatTime("$endTime") """
  }

  def jsResult(func: String) = {

    val toRun = s"function () {return $func}()"

    <script>document.write({xml.Unparsed(toRun)})</script>
  }

}





