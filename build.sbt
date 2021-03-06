
enablePlugins(JavaAppPackaging)

val spark = Seq(
    "org.apache.spark" %% "spark-core" % "2.1.0",
    "org.apache.spark" %% "spark-mllib" % "2.1.0",
    "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.1.0",
    "org.apache.kafka" % "connect-json" % "0.10.1.1"
  )

lazy val commonSettings = Seq(
    organization := "name.ebastien.spark",
    version := "0.1.0",
    scalaVersion := "2.11.8"
  )

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    name := "playground",
    libraryDependencies ++= spark
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Ywarn-unused-import"
)
