name := "cstream"

version := "1.0"

scalaVersion := "2.12.2"

val akkaVersion = "2.5.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"          % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit"        % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream"         % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "org.scalatest"     %% "scalatest"           % "3.0.1" % Test,
  "org.typelevel"     %% "cats"                % "0.9.0"
)

scalacOptions += "-Xlog-implicits"
