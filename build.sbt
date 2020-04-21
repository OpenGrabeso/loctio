import sbt.Keys.scalacOptions
// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

githubActor in ThisBuild := sys.env.getOrElse("GITHUB_USERNAME", "OpenGrabeso")

githubTokenSource in ThisBuild := TokenSource.GitConfig("github.token") || TokenSource.Environment("GITHUB_USERTOKEN") || TokenSource.Environment("GITHUB_TOKEN")

resolvers in ThisBuild += Resolver.githubPackages("OpenGrabeso", "packages")

lazy val commonSettings = Seq(
  organization := "com.github.opengrabeso",
  version := "0.4.1-beta",
  scalaVersion := "2.12.10",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
)

lazy val jsCommonSettings = Seq(
  scalacOptions ++= Seq("-P:scalajs:sjsDefinedByDefault")
)

val udashVersion = "0.8.2"

val bootstrapVersion = "4.3.1"

val udashJQueryVersion = "3.0.1"

// TODO: try to share
lazy val jvmLibs = Seq(
  "org.scalatest" %% "scalatest" % "3.1.0" % "test",

  "io.udash" %% "udash-core" % udashVersion,
  "io.udash" %% "udash-rest" % udashVersion,
  "io.udash" %% "udash-rpc" % udashVersion,
  "io.udash" %% "udash-css" % udashVersion,
)

lazy val jsLibs = libraryDependencies ++= Seq(
  "org.scalatest" %%% "scalatest" % "3.1.0" % "test",
  "org.scala-js" %%% "scalajs-dom" % "0.9.7",
  "org.querki" %%% "jquery-facade" % "1.2",

  "io.udash" %%% "udash-core" % udashVersion,
  "io.udash" %%% "udash-rest" % udashVersion,
  "io.udash" %%% "udash-rpc" % udashVersion,
  "io.udash" %%% "udash-css" % udashVersion,

  "io.udash" %%% "udash-bootstrap4" % udashVersion,
  "io.udash" %%% "udash-charts" % udashVersion,
  "io.udash" %%% "udash-jquery" % udashJQueryVersion,

  "com.zoepepper" %%% "scalajs-jsjoda" % "1.1.1",
  "com.zoepepper" %%% "scalajs-jsjoda-as-java-time" % "1.1.1"
)

lazy val jsDeps = jsDependencies ++= Seq(
  // "jquery.js" is provided by "udash-jquery" dependency
  "org.webjars" % "bootstrap" % bootstrapVersion / "bootstrap.bundle.js" minified "bootstrap.bundle.min.js" dependsOn "jquery.js",
  "org.webjars.npm" % "js-joda" % "1.10.1" / "dist/js-joda.js" minified "dist/js-joda.min.js"
)

lazy val commonLibs = Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.2.0"
)

val jacksonVersion = "2.9.9"

lazy val sharedJs = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure).in(file("shared-js"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(commonSettings)
  .jvmSettings(libraryDependencies ++= jvmLibs)
  .jsSettings(
    jsCommonSettings,
    jsLibs,
    jsDeps
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
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.0.9",
    libraryDependencies += "com.twelvemonkeys.imageio" % "imageio-bmp" % "3.5",
    libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "2.1.1",
    libraryDependencies += "org.xhtmlrenderer" % "flying-saucer-core" % "9.1.20-opengrabeso.4",
    libraryDependencies ++= commonLibs ++ jvmLibs,
    assemblyJarName in assembly := "loctio-tray.jar",
    assemblyMergeStrategy in assembly := {
      case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )

def inDevMode = true || sys.props.get("dev.mode").exists(value => value.equalsIgnoreCase("true"))

def addJavaScriptToServerResources(): Def.SettingsDefinition = {
  val optJs = if (inDevMode) fastOptJS else fullOptJS
  (resources in Compile) += (optJs in(frontend, Compile)).value.data
}

def addJSDependenciesToServerResources(): Def.SettingsDefinition = {
  val depJs = if (inDevMode) packageJSDependencies else packageMinifiedJSDependencies
  (resources in Compile) += (depJs in(frontend, Compile)).value
}

lazy val frontend = project.settings(
    commonSettings,
    jsCommonSettings,
    jsLibs
  ).enablePlugins(ScalaJSPlugin)
    .dependsOn(sharedJs_JS)

lazy val backend = (project in file("backend"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .dependsOn(shared, sharedJs_JVM)
  .settings(
    addJavaScriptToServerResources(),
    addJSDependenciesToServerResources(),

    resourceGenerators in Compile += Def.task {
      val file = (resourceManaged in Compile).value / "config.properties"
      val contents = s"devMode=${inDevMode}"
      IO.write(file, contents)
      Seq(file)
    }.taskValue,

    commonSettings,

    libraryDependencies ++= commonLibs ++ jvmLibs ++ Seq(
      "com.google.http-client" % "google-http-client-appengine" % "1.31.0",
      "com.google.http-client" % "google-http-client-jackson2" % "1.31.0",
      "com.google.apis" % "google-api-services-storage" % "v1-rev158-1.25.0",
      "com.google.appengine.tools" % "appengine-gcs-client" % "0.8" exclude("javax.servlet", "servlet.api"),
      "com.google.cloud" % "google-cloud-storage" % "1.96.0",

      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,

      "com.fasterxml" % "aalto-xml" % "1.0.0",

      //"org.webjars" % "webjars-locator-core" % "0.39",

      "fr.opensagres.xdocreport.appengine-awt" % "appengine-awt" % "1.0.0",

      "com.sparkjava" % "spark-core" % "1.1.1" excludeAll ExclusionRule(organization = "org.eclipse.jetty"),
      "org.slf4j" % "slf4j-simple" % "1.6.1",
      "commons-fileupload" % "commons-fileupload" % "1.3.2",
      "com.jsuereth" %% "scala-arm" % "2.0" exclude(
        "org.scala-lang.plugins", "scala-continuations-library_" + scalaBinaryVersion.value
      ),
      "org.apache.commons" % "commons-math" % "2.1",
      "commons-io" % "commons-io" % "2.1"
    )
  )

lazy val jetty = (project in file("jetty")).dependsOn(backend).settings(
  libraryDependencies ++= Seq(
    // "javax.servlet" % "javax.servlet-api" % "4.0.1", // version 3.1.0 provided by the jetty-server should be fine
    "org.eclipse.jetty" % "jetty-server" % "9.3.18.v20170406"
  )
)

lazy val root = (project in file(".")).aggregate(backend).settings(
  name := "Loctio"
)