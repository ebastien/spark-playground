
enablePlugins(JavaAppPackaging)

val spark = Seq(
    "org.apache.spark" %% "spark-core" % "2.1.0",
    "org.apache.spark" %% "spark-mllib" % "2.1.0"
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
