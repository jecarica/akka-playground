val scala = "2.13.10"

val catsVersion           = "2.7.0"
val akkaVersion           = "2.8.1"
val alpakkaVersion        = "6.0.1"
val akkaHttpVersion       = "10.2.9"
val akkaManagementVersion = "1.1.3"

lazy val commonSettings = Seq(
  organization := "cn.xuyinyin",
  name         := "akka-playground",
  maintainer   := "jia_yangchen@163.com, aka xujiawei",
  version      := "0.2",
  scalaVersion := scala,
  Compile / scalacOptions ++= Seq("-Ymacro-annotations", "-feature"),
  Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation", "-source", "1.8", "-target", "1.8"),
  run / fork               := true,
  Global / cancelable      := false,
  Test / parallelExecution := false,
  libraryDependencies ++= Seq(
    "ch.qos.logback"              % "logback-classic" % "1.2.11",
    "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.4",
    "org.scalatest"              %% "scalatest"       % "3.2.12" % Test,
    "org.typelevel"              %% "cats-core"       % catsVersion
  )
)

lazy val root = Project(id = "akka-playground", base = file("."))
  .settings(commonSettings)
  .aggregate(akkaServer)

import com.typesafe.sbt.packager.docker.Cmd

lazy val akkaServer = Project(id = "akka-server", base = file("akka-server"))
  .enablePlugins(JavaServerAppPackaging, DockerPlugin, AshScriptPlugin)
  .settings(
    commonSettings ++ Seq(
      Compile / mainClass := Some("cn.xuyinyin.akka.Main"),
      Universal / javaOptions ++= Seq("-J-Xms256m", "-J-Xmx512m"),
      bashScriptExtraDefines += """addJava "-Dconfig.file=${app_home}/../conf/application.conf"""",
      Docker / packageName               := "akka",
      Docker / version                   := "0.2",
      Docker / daemonUser                := "akka",
      dockerBaseImage                    := "openjdk:8-jre-alpine",
      dockerExposedPorts                 := Seq(3300),
      dockerUpdateLatest                 := true,
      unmanagedResources / excludeFilter := "application.conf",
      dockerCommands ++= Seq(
        Cmd("USER", "root"),
        Cmd("RUN", "ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime"),
        Cmd("RUN", "echo 'Asia/Shanghai' > /etc/timezone")
      )
    ),
    libraryDependencies ++= Seq(
      // akka-typed
      "com.typesafe.akka" %% "akka-actor-typed"            % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed"          % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson"  % akkaVersion,

      "com.lightbend.akka" %% "akka-stream-alpakka-csv"    % alpakkaVersion,
      "com.lightbend.akka" %% "akka-stream-alpakka-ftp"    % alpakkaVersion,

      // akka-http
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream-typed"    % akkaVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,

      // akka testkit
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-multi-node-testkit"  % akkaVersion     % Test,

      // akka management
      "com.typesafe.akka"             %% "akka-discovery"                    % akkaVersion,
      "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % akkaManagementVersion,
      "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % akkaManagementVersion,

      // akka-persistence
      "com.typesafe.akka"  %% "akka-persistence-typed" % akkaVersion,
      "com.typesafe.akka"  %% "akka-persistence-query" % akkaVersion,
      "com.lightbend.akka" %% "akka-persistence-jdbc"  % "5.1.0",

    )
  )
