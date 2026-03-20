val scala3Version     = "3.5.2"

val zioVersion        = "2.1.13"
val zioAwsVersion     = "7.28.16.2"
val zioRedisVersion   = "1.0.0"
val slickVersion      = "3.5.2"
val scanamoVersion    = "2.0.0"
val sttpVersion       = "3.10.1"
val circeVersion      = "0.14.10"
val awsLambdaVersion  = "1.2.3"
val zioConfigVersion  = "4.0.3"

lazy val root = (project in file("."))
  .settings(
    name         := "pipeline",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    organization := "com.pipeline",

    scalacOptions ++= Seq(
      "-Xcheck-macros",
      "-deprecation",
      "-feature",
      "-unchecked",
    ),

    libraryDependencies ++= Seq(
      // ── ZIO Core ─────────────────────────────────────────────
      "dev.zio" %% "zio"         % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,

      // ── ZIO AWS ──────────────────────────────────────────────
      "dev.zio" %% "zio-aws-kinesis"    % zioAwsVersion,
      "dev.zio" %% "zio-aws-dynamodb"   % zioAwsVersion,
      "dev.zio" %% "zio-aws-cloudwatch" % zioAwsVersion,
      "dev.zio" %% "zio-aws-sqs"        % zioAwsVersion,
      "dev.zio" %% "zio-aws-netty"      % zioAwsVersion,

      // ── ZIO Redis ─────────────────────────────────────────────
      "dev.zio" %% "zio-redis" % zioRedisVersion,

      // ── Slick + PostgreSQL ────────────────────────────────────
      "com.typesafe.slick" %% "slick"        % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
      "org.postgresql"      % "postgresql"   % "42.7.4",

      // ── Scanamo (DynamoDB) ────────────────────────────────────
      "org.scanamo" %% "scanamo"     % scanamoVersion,
      "org.scanamo" %% "scanamo-zio" % scanamoVersion,

      // ── sttp3 ─────────────────────────────────────────────────
      "com.softwaremill.sttp.client3" %% "core"  % sttpVersion,
      "com.softwaremill.sttp.client3" %% "zio"   % sttpVersion,
      "com.softwaremill.sttp.client3" %% "circe" % sttpVersion,

      // ── Circe ─────────────────────────────────────────────────
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,

      // ── AWS Lambda ────────────────────────────────────────────
      "com.amazonaws" % "aws-lambda-java-core"   % awsLambdaVersion,
      "com.amazonaws" % "aws-lambda-java-events" % "3.14.0",

      // ── Config ────────────────────────────────────────────────
      "dev.zio" %% "zio-config"          % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,

      // ── Logging ───────────────────────────────────────────────
      "dev.zio" %% "zio-logging"       % "2.4.0",
      "dev.zio" %% "zio-logging-slf4j" % "2.4.0",

      // ── Test ──────────────────────────────────────────────────
      "dev.zio" %% "zio-test"          % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt"      % zioVersion % Test,
      "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
    ),

    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

    // ── sbt-assembly fat JAR para Lambda ─────────────────────────
    assembly / assemblyJarName := "pipeline-assembly.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
      case PathList("META-INF", xs @ _*)             => MergeStrategy.discard
      case "reference.conf"                           => MergeStrategy.concat
      case "application.conf"                         => MergeStrategy.concat
      case _                                          => MergeStrategy.first
    },

    // ── Cobertura ─────────────────────────────────────────────────
    coverageMinimumStmtTotal := 80,
    coverageFailOnMinimum    := false,
  )
