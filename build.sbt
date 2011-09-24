organization := "com.example"

name := "rsvp-unfiltered"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
  "net.databinder" %% "unfiltered-filter" % "0.5.0",
  "net.databinder" %% "unfiltered-jetty" % "0.5.0",
  "net.databinder" %% "unfiltered-spec" % "0.5.0" % "test",
  "org.scala-tools.testing" %% "specs" % "1.6.9" % "test",
  "net.databinder" %% "dispatch-http" % "0.8.5" % "test"
)

resolvers ++= Seq(
  "java m2" at "http://download.java.net/maven/2"
)
