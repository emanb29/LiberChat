lazy val supportedScalaVersions = List("2.13.1", "2.12.10")

ThisBuild / organization := "me.ethanbell"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.1"
ThisBuild / name := "LiberChat"

val scalaTestV    = "3.1.1"
val scalaCheckV   = "1.14.1"
val akkaStreamsV  = "2.6.4"
val akkaV         = "2.6.5"
val fastParseV    = "2.2.2"
val logbackV      = "1.2.3"
val scalaLoggingV = "3.9.2"

lazy val commonSettings = List(
  scalacOptions ++= Seq(
        "-encoding",
        "utf8",
        "-deprecation",
        "-unchecked",
        "-Xlint",
        "-feature",
        "-language:existentials",
        "-language:experimental.macros",
        "-language:higherKinds",
        "-language:implicitConversions",
        "-Ypartial-unification",
        "-Yrangepos"
      ),
  libraryDependencies ++= Seq(
        "com.typesafe.akka"          %% "akka-actor-typed" % akkaV,
        "com.typesafe.akka"          %% "akka-stream"      % akkaStreamsV,
        "com.lihaoyi"                %% "fastparse"        % fastParseV,
        "ch.qos.logback"             % "logback-classic"   % logbackV,
        "com.typesafe.scala-logging" %% "scala-logging"    % scalaLoggingV,
        "org.scalatest"              %% "scalatest"        % scalaTestV % "test",
        "org.scalacheck"             %% "scalacheck"       % scalaCheckV % "test"
      ),
  crossScalaVersions := supportedScalaVersions,
  scalacOptions ++= (scalaVersion.value match {
        case VersionNumber(Seq(2, 12, _*), _, _) | VersionNumber(Seq(2, 13, _*), _, _) =>
          Seq("-Xfatal-warnings")
        case _ => Nil
      }),
  scalacOptions --= (scalaVersion.value match {
        case VersionNumber(Seq(2, 13, _*), _, _) =>
          Seq("-Ypartial-unification")
        case _ => Nil
      }),
  Test / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / console / scalacOptions --= Seq("-deprecation", "-Xfatal-warnings", "-Xlint"),
  scalafmtOnCompile := true
)
lazy val common = (project in file("common/")).settings(commonSettings)
lazy val client = (project in file("client/")).settings(commonSettings).dependsOn(common)
lazy val server = (project in file("server/")).settings(commonSettings).dependsOn(common)

Global / onLoad ~= (_ andThen ("project server" :: _))
