name := "akkarouter"
organization := "com.ericsson.ema"
version := "1.0"

scalaVersion := "2.11.7"

val akkaVersion = "2.4.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "org.slf4j" % "slf4j-log4j12" % "1.7.5",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test"
)

