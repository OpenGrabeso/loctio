import sbt.Keys.scalacOptions
// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

lazy val resolverSettings = Seq(
  githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource.Environment("BUILD_TOKEN") || TokenSource.Environment("GITHUB_TOKEN"),
  resolvers += Resolver.githubPackages("OpenGrabeso")
)

lazy val commonSettings = Seq(
  organization := "com.github.opengrabeso",
  version := "0.6.0",
  scalaVersion := "2.13.12",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8")
) ++ resolverSettings

lazy val jsCommonSettings = Seq(
  libraryDependencies += "com.zoepepper" %%% "scalajs-jsjoda" % "1.2.0",
  libraryDependencies += "com.zoepepper" %%% "scalajs-jsjoda-as-java-time" % "1.2.0",
  excludeDependencies += ExclusionRule(organization = "io.github.cquiroz") // workaround for https://github.com/cquiroz/scala-java-time/issues/257
)

lazy val flyingSaucersSettings = Seq(
  libraryDependencies += "org.xhtmlrenderer" % "flying-saucer-core" % "9.1.20-opengrabeso.4"
)

val udashVersion = "0.10.0"

val bootstrapVersion = "4.3.1"

val udashJQueryVersion = "3.0.4"

// TODO: try to share
lazy val jvmLibs = Seq(
  "org.scalatest" %% "scalatest" % "3.2.9" % "test",

  "io.udash" %% "udash-core" % udashVersion,
  "io.udash" %% "udash-rest" % udashVersion,
  "io.udash" %% "udash-rpc" % udashVersion,
  "io.udash" %% "udash-css" % udashVersion,
)

lazy val jsLibs = libraryDependencies ++= Seq(
  "org.scalatest" %%% "scalatest" % "3.2.9" % "test",
  "org.scala-js" %%% "scalajs-dom" % "2.4.0",
  "org.querki" %%% "jquery-facade" % "2.1",

  "io.udash" %%% "udash-core" % udashVersion,
  "io.udash" %%% "udash-rest" % udashVersion,
  "io.udash" %%% "udash-rpc" % udashVersion,
  "io.udash" %%% "udash-css" % udashVersion,

  "io.udash" %%% "udash-bootstrap4" % udashVersion,
  "io.udash" %%% "udash-jquery" % udashJQueryVersion,


)

lazy val jsDeps = jsDependencies ++= Seq(
  // "jquery.js" is provided by "udash-jquery" dependency
  "org.webjars" % "bootstrap" % bootstrapVersion / "bootstrap.bundle.js" minified "bootstrap.bundle.min.js" dependsOn "jquery.js",
  "org.webjars.npm" % "js-joda" % "1.10.1" / "dist/js-joda.js" minified "dist/js-joda.min.js"
)

lazy val commonLibs = Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)

val jacksonVersion = "2.14.2"

lazy val sharedJs = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure).in(file("shared-js"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .jsConfigure(_.enablePlugins(JSDependenciesPlugin))
  .settings(
    commonSettings,
  )
  .jvmSettings(libraryDependencies ++= jvmLibs)
  .jsSettings(
    jsCommonSettings,
    jsLibs,
    jsDeps,
  )

lazy val sharedJs_JVM = sharedJs.jvm
lazy val sharedJs_JS = sharedJs.js

lazy val shared = (project in file("shared"))
  .dependsOn(sharedJs.jvm)
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    commonSettings,
    libraryDependencies ++= commonLibs
  )


lazy val trayUtil = (project in file("tray-util"))
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(shared, sharedJs_JVM)
  .settings(
    name := "LoctioStart",
    commonSettings,
    flyingSaucersSettings,
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.5.0",
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % "2.7.0",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.7.0",
    libraryDependencies += "com.softwaremill.sttp.client3" %% "okhttp-backend" % "3.8.14",
    libraryDependencies += "com.twelvemonkeys.imageio" % "imageio-bmp" % "3.5",
    libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "2.1.1",
    libraryDependencies += "net.java.dev.jna" % "jna" % "5.5.0",
    libraryDependencies ++= commonLibs ++ jvmLibs,
    assembly / assemblyJarName := "loctio-tray.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class") => MergeStrategy.discard // not creating library, discard should be OK
      case path if path.endsWith("/module-info.class") => MergeStrategy.discard
      case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
      case x =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )

def inDevMode = true || sys.props.get("dev.mode").exists(value => value.equalsIgnoreCase("true"))

def addJavaScriptToServerResources(): Def.SettingsDefinition = {
  val optJs = if (inDevMode) fastOptJS else fullOptJS
  (Compile / resources) += (frontend / Compile / optJs).value.data
}

def addJSDependenciesToServerResources(): Def.SettingsDefinition = {
  val depJs = if (inDevMode) packageJSDependencies else packageMinifiedJSDependencies
  (Compile / resources) += (frontend / Compile / depJs).value
}

lazy val frontend = project.settings(
    commonSettings,
    jsCommonSettings,
    jsLibs
  ).enablePlugins(ScalaJSPlugin, JSDependenciesPlugin)
    .dependsOn(sharedJs_JS)

lazy val backend = (project in file("backend"))
  .enablePlugins(sbtassembly.AssemblyPlugin, JavaAppPackaging)
  .dependsOn(shared, sharedJs_JVM)
  .settings(
    addJavaScriptToServerResources(),
    addJSDependenciesToServerResources(),

    Compile / resourceGenerators += Def.task {
      import Path._
      val configFile = (Compile / resourceManaged).value / "config.properties"
      IO.write(configFile, s"devMode=$inDevMode\ndummy=false")

      // from https://stackoverflow.com/a/57994298/16673
      val staticDir = baseDirectory.value / "web" / "static"
      val staticFiles = (staticDir ** "*.*").get()
      val pairs = staticFiles pair rebase(staticDir, (Compile / resourceManaged).value / "static")

      IO.copy(pairs)

      configFile +: pairs.map(_._2)
    }.taskValue,

    commonSettings,
    flyingSaucersSettings,

    libraryDependencies ++= commonLibs ++ jvmLibs ++ Seq(
      "com.google.http-client" % "google-http-client-appengine" % "1.39.0",
      "com.google.http-client" % "google-http-client-jackson2" % "1.39.0",
      "com.google.apis" % "google-api-services-storage" % "v1-rev171-1.25.0",

      "com.softwaremill.sttp.client3" %% "okhttp-backend" % "3.8.14",

      "org.eclipse.jetty" % "jetty-server" % "9.4.31.v20200723",
      "org.eclipse.jetty" % "jetty-servlet" % "9.4.31.v20200723",

      "com.google.cloud" % "google-cloud-storage" % "1.118.0",
      "com.google.cloud" % "google-cloud-tasks" % "1.33.2",

      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,

      "com.fasterxml" % "aalto-xml" % "1.0.0",
      "org.jsoup" % "jsoup" % "1.13.1",

      //"org.webjars" % "webjars-locator-core" % "0.39",

      "org.slf4j" % "slf4j-simple" % "1.6.1",
      "org.apache.commons" % "commons-math" % "2.1",
      "commons-io" % "commons-io" % "2.1"
    ),

    excludeDependencies ++= Seq(
      ExclusionRule(organization = "io.netty"), // netty needed for the Tray util, but not for the backend (using Jetty)
      ExclusionRule(organization = "io.github.cquiroz") // we do not need this on JVM - workaround for https://github.com/cquiroz/scala-java-time/issues/257
    ),

    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") =>
        MergeStrategy.discard
      case PathList(ps @ _*) if ps.nonEmpty && Seq("io.netty.versions.properties", "module-info.class", "nowarn$.class", "nowarn.class").contains(ps.last) =>
        MergeStrategy.first
      case PathList("javax", "servlet", _*) =>
        MergeStrategy.first
      case PathList("META-INF", ps @ _*) if ps.nonEmpty && Seq("native-image.properties", "reflection-config.json").contains(ps.last) =>
        MergeStrategy.first
      case PathList("module-info.class") => MergeStrategy.discard // not creating library, discard should be OK
      case path if path.endsWith("/module-info.class") => MergeStrategy.discard
      case x =>
        // default handling for things like INDEX.LIST (see https://stackoverflow.com/a/46287790/16673)
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(x)
    },

    assembly / assemblyJarName := "loctio.jar",
    assembly / mainClass := Some("com.github.opengrabeso.loctio.DevServer"),

    Universal / javaOptions ++= Seq(
      // -J params will be added as jvm parameters
      "-J-Xmx256M" // see https://www.scala-sbt.org/sbt-native-packager/archetypes/java_app/customize.html
      // note: app.yaml is no longer used, we need to specify options here
      // -XX:MaxMetaspaceSize=56M -XX:+UseSerialGC -XX:ReservedCodeCacheSize=20M -jar loctio.jar
    ),

    Docker / packageName := "loctio",
    dockerExposedPorts := Seq(8080),
    dockerBaseImage := "openjdk:11-jre-slim",
  )

lazy val root = (project in file(".")).aggregate(backend).settings(
  name := "Loctio",
  resolverSettings
)
