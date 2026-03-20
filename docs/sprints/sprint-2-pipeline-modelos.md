# Sprint 2 — Pipeline: Modelos & Configuración Base

## Objetivo

Establecer el proyecto Scala/ZIO con todas las dependencias declaradas, los modelos de dominio definidos y sus codecs JSON validados mediante tests.

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 0 completado

---

## 1. Estructura del Proyecto

```
pipeline/
├── src/
│   ├── main/scala/com/pipeline/
│   │   ├── Main.scala              # entrypoint Lambda
│   │   ├── config/
│   │   │   └── AppConfig.scala
│   │   ├── domain/
│   │   │   ├── InputMessage.scala
│   │   │   ├── PerformanceInterval.scala
│   │   │   └── AuditRecord.scala
│   │   └── codec/
│   │       └── JsonCodecs.scala
│   └── test/scala/com/pipeline/
│       ├── domain/
│       │   └── ModelSpec.scala
│       └── codec/
│           └── JsonCodecSpec.scala
├── project/
│   ├── build.properties
│   └── plugins.sbt
└── build.sbt
```

---

## 2. `project/build.properties`

```properties
sbt.version=1.10.7
```

---

## 3. `project/plugins.sbt`

```sbt
addSbtPlugin("com.eed3si9n"      % "sbt-assembly"       % "2.2.0")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"       % "2.5.2")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.5.1")
```

---

## 4. `build.sbt`

```sbt
val scala3Version = "3.5.2"

val zioVersion        = "2.1.13"
val zioAwsVersion     = "7.28.16.2"
val zioRedisVersion   = "1.0.0"
val slickVersion      = "3.5.2"
val scanamoVersion    = "2.0.0"
val sttpVersion       = "3.10.1"
val circeVersion      = "0.14.10"
val awsLambdaVersion  = "1.2.3"

lazy val root = (project in file("."))
  .settings(
    name         := "pipeline",
    version      := "0.1.0",
    scalaVersion := scala3Version,
    scalacOptions ++= Seq("-Xcheck-macros"),

    libraryDependencies ++= Seq(
      // ZIO Core
      "dev.zio"             %% "zio"                      % zioVersion,
      "dev.zio"             %% "zio-streams"              % zioVersion,

      // ZIO AWS
      "dev.zio"             %% "zio-aws-kinesis"          % zioAwsVersion,
      "dev.zio"             %% "zio-aws-dynamodb"         % zioAwsVersion,
      "dev.zio"             %% "zio-aws-cloudwatch"       % zioAwsVersion,
      "dev.zio"             %% "zio-aws-netty"            % zioAwsVersion,

      // ZIO Redis
      "dev.zio"             %% "zio-redis"                % zioRedisVersion,

      // Slick + PostgreSQL
      "com.typesafe.slick"  %% "slick"                    % slickVersion,
      "com.typesafe.slick"  %% "slick-hikaricp"           % slickVersion,
      "org.postgresql"       % "postgresql"               % "42.7.4",

      // Scanamo (DynamoDB)
      "org.scanamo"         %% "scanamo"                  % scanamoVersion,
      "org.scanamo"         %% "scanamo-zio"              % scanamoVersion,

      // sttp3
      "com.softwaremill.sttp.client3" %% "core"           % sttpVersion,
      "com.softwaremill.sttp.client3" %% "zio"            % sttpVersion,
      "com.softwaremill.sttp.client3" %% "circe"          % sttpVersion,

      // Circe
      "io.circe"            %% "circe-core"               % circeVersion,
      "io.circe"            %% "circe-generic"            % circeVersion,
      "io.circe"            %% "circe-parser"             % circeVersion,

      // AWS Lambda
      "com.amazonaws"        % "aws-lambda-java-core"     % awsLambdaVersion,
      "com.amazonaws"        % "aws-lambda-java-events"   % "3.14.0",

      // Config
      "dev.zio"             %% "zio-config"               % "4.0.3",
      "dev.zio"             %% "zio-config-typesafe"      % "4.0.3",
      "dev.zio"             %% "zio-config-magnolia"      % "4.0.3",

      // Test
      "dev.zio"             %% "zio-test"                 % zioVersion % Test,
      "dev.zio"             %% "zio-test-sbt"             % zioVersion % Test,
      "dev.zio"             %% "zio-test-magnolia"        % zioVersion % Test,
    ),

    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),

    // sbt-assembly: fat JAR para Lambda
    assembly / assemblyJarName := "pipeline-assembly.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", _*) => MergeStrategy.concat
      case PathList("META-INF", _*)             => MergeStrategy.discard
      case "reference.conf"                      => MergeStrategy.concat
      case _                                     => MergeStrategy.first
    }
  )
```

---

## 5. Modelos de Dominio

### `domain/InputMessage.scala`

```scala
package com.pipeline.domain

import java.time.Instant

final case class InputMessage(
  nodeId:         String,
  dttmUtc:        Instant,
  registrationId: Option[String],
  baselineId:     Option[String]
):
  /** Exactamente uno de los dos debe estar presente */
  def isValid: Boolean =
    (registrationId.isDefined, baselineId.isDefined) match
      case (true, false) | (false, true) => true
      case _                              => false

  def resolvedId: Option[String] =
    registrationId.orElse(baselineId)

  def isRegistration: Boolean = registrationId.isDefined
```

### `domain/PerformanceInterval.scala`

```scala
package com.pipeline.domain

import java.time.Instant

final case class PerformanceInterval(
  dispatchUnit:  String,
  nodeId:        String,
  dttmUtc:       Instant,
  meteredValue:  Option[Double] = None,
  baselineValue: Option[Double] = None,
  baselineId:    Option[String] = None
):
  /** Merge: conserva campos del objeto previo, sobreescribe solo los definidos en `newer` */
  def merge(newer: PerformanceInterval): PerformanceInterval =
    copy(
      meteredValue  = newer.meteredValue.orElse(meteredValue),
      baselineValue = newer.baselineValue.orElse(baselineValue),
      baselineId    = newer.baselineId.orElse(baselineId)
    )
```

### `domain/AuditRecord.scala`

```scala
package com.pipeline.domain

import java.time.Instant

final case class AuditRecord(
  nodeId:         String,
  dttmUtc:        String,   // ISO-8601 String para DynamoDB
  registrationId: Option[String],
  baselineId:     Option[String],
  receivedAt:     String,   // ISO-8601
  ttl:            Long      // epoch seconds = dttm_utc + 24h
)

object AuditRecord:
  def from(msg: InputMessage, now: Instant): AuditRecord =
    AuditRecord(
      nodeId         = msg.nodeId,
      dttmUtc        = msg.dttmUtc.toString,
      registrationId = msg.registrationId,
      baselineId     = msg.baselineId,
      receivedAt     = now.toString,
      ttl            = msg.dttmUtc.plusSeconds(86400).getEpochSecond
    )
```

---

## 6. Codecs Circe

### `codec/JsonCodecs.scala`

```scala
package com.pipeline.codec

import com.pipeline.domain.*
import io.circe.*
import io.circe.generic.semiauto.*
import java.time.Instant

object JsonCodecs:

  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] = Decoder.decodeString.emapTry(s => scala.util.Try(Instant.parse(s)))

  given Encoder[InputMessage]        = deriveEncoder
  given Decoder[InputMessage]        = deriveDecoder
  given Encoder[PerformanceInterval] = deriveEncoder
  given Decoder[PerformanceInterval] = deriveDecoder
  given Encoder[AuditRecord]         = deriveEncoder
  given Decoder[AuditRecord]         = deriveDecoder
```

---

## 7. Configuración de la Aplicación

### `config/AppConfig.scala`

```scala
package com.pipeline.config

import zio.*
import zio.config.*
import zio.config.magnolia.*

final case class KinesisConfig(
  endpoint:          String,
  region:            String,
  inputStreamName:   String,
  outputStreamName:  String
)

final case class DynamoConfig(
  endpoint:   String,
  region:     String,
  tableName:  String
)

final case class RedisConfig(
  host: String,
  port: Int
)

final case class PostgresConfig(
  host:     String,
  port:     Int,
  database: String,
  user:     String,
  password: String
)

final case class ValueBackendConfig(
  baseUrl: String
)

final case class AppConfig(
  kinesis:      KinesisConfig,
  dynamo:       DynamoConfig,
  redis:        RedisConfig,
  postgres:     PostgresConfig,
  valueBackend: ValueBackendConfig
)

object AppConfig:
  val layer: ULayer[AppConfig] =
    ZLayer.fromZIO(
      read(deriveConfig[AppConfig].from(ConfigSource.fromSystemEnv))
        .orDie
    )
```

Variables de entorno esperadas:

| Variable | Ejemplo |
|---|---|
| `KINESIS_ENDPOINT` | `http://localhost:4566` |
| `KINESIS_REGION` | `us-east-1` |
| `KINESIS_INPUT_STREAM_NAME` | `input-stream` |
| `KINESIS_OUTPUT_STREAM_NAME` | `output-stream` |
| `DYNAMO_ENDPOINT` | `http://localhost:4566` |
| `DYNAMO_REGION` | `us-east-1` |
| `DYNAMO_TABLE_NAME` | `audit-records` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DATABASE` | `pipeline` |
| `POSTGRES_USER` | `pipeline` |
| `POSTGRES_PASSWORD` | `pipeline` |
| `VALUE_BACKEND_BASE_URL` | `http://localhost:3333` |

---

## 8. Tests

### `domain/ModelSpec.scala`

```scala
package com.pipeline.domain

import zio.test.*
import java.time.Instant

object ModelSpec extends ZIOSpecDefault:
  def spec = suite("InputMessage")(
    test("es válido con solo registrationId") {
      val msg = InputMessage("n1", Instant.now(), Some("reg-1"), None)
      assertTrue(msg.isValid, msg.isRegistration, msg.resolvedId.contains("reg-1"))
    },
    test("es válido con solo baselineId") {
      val msg = InputMessage("n1", Instant.now(), None, Some("base-1"))
      assertTrue(msg.isValid, !msg.isRegistration, msg.resolvedId.contains("base-1"))
    },
    test("es inválido si ambos están presentes") {
      val msg = InputMessage("n1", Instant.now(), Some("r"), Some("b"))
      assertTrue(!msg.isValid)
    },
    test("es inválido si ninguno está presente") {
      val msg = InputMessage("n1", Instant.now(), None, None)
      assertTrue(!msg.isValid)
    }
  ) +
  suite("PerformanceInterval.merge")(
    test("merge preserva campos previos y aplica los nuevos") {
      val prev = PerformanceInterval("du-1", "n1", Instant.now(), meteredValue = Some(1000.0))
      val next = PerformanceInterval("du-1", "n1", Instant.now(), baselineValue = Some(950.0), baselineId = Some("b1"))
      val merged = prev.merge(next)
      assertTrue(
        merged.meteredValue.contains(1000.0),
        merged.baselineValue.contains(950.0),
        merged.baselineId.contains("b1")
      )
    }
  )
```

---

## Criterios de Aceptación

- [ ] `sbt compile` finaliza sin errores
- [ ] `sbt test` pasa todos los tests de modelos y codecs
- [ ] Las case classes reflejan exactamente los esquemas del README
- [ ] Los codecs Circe serializan/deserializan sin pérdida de datos
- [ ] `AppConfig` lee correctamente de variables de entorno
