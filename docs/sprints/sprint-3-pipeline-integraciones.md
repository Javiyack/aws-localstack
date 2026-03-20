# Sprint 3 — Pipeline: Integraciones con AWS

## Objetivo

Implementar y testear de forma aislada cada capa de acceso a datos: Kinesis, DynamoDB, Redis y PostgreSQL. Al final de este sprint cada repositorio funciona correctamente contra sus servicios locales (LocalStack / Docker).

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 1 (value-backend levantado), Sprint 2 (modelos y config)

---

## 1. Kinesis — Consumer & Producer

### `infrastructure/kinesis/KinesisConsumer.scala`

```scala
package com.pipeline.infrastructure.kinesis

import com.pipeline.config.KinesisConfig
import com.pipeline.domain.InputMessage
import com.pipeline.codec.JsonCodecs.given
import io.circe.parser.decode
import zio.*
import zio.aws.kinesis.Kinesis
import zio.aws.kinesis.model.*
import zio.stream.ZStream
import java.nio.charset.StandardCharsets

trait KinesisConsumer:
  def records: ZStream[Any, Throwable, InputMessage]

object KinesisConsumer:
  val live: ZLayer[Kinesis & KinesisConfig, Nothing, KinesisConsumer] =
    ZLayer.fromFunction { (kinesis: Kinesis, cfg: KinesisConfig) =>
      new KinesisConsumer:
        def records: ZStream[Any, Throwable, InputMessage] =
          // En producción Lambda recibe el evento directamente —
          // este consumer es útil para pruebas locales / dashboard
          ZStream.fromZIO(
            kinesis.getShardIterator(
              GetShardIteratorRequest(
                streamName          = cfg.inputStreamName,
                shardId             = "shardId-000000000000",
                shardIteratorType   = ShardIteratorType.TRIM_HORIZON
              )
            ).mapError(_.toThrowable)
          ).flatMap { iterResp =>
            ZStream.paginateZIO(iterResp.shardIterator.getOrElse("")) { iterator =>
              kinesis.getRecords(GetRecordsRequest(shardIterator = iterator, limit = Some(500)))
                .mapError(_.toThrowable)
                .map { resp =>
                  val msgs = resp.records.flatMap { r =>
                    val json = new String(r.data.toArray, StandardCharsets.UTF_8)
                    decode[InputMessage](json).toOption
                  }
                  (msgs, resp.nextShardIterator)
                }
            }
          }.flatMap(ZStream.fromIterable)
    }
```

### `infrastructure/kinesis/KinesisProducer.scala`

```scala
package com.pipeline.infrastructure.kinesis

import com.pipeline.config.KinesisConfig
import com.pipeline.domain.PerformanceInterval
import com.pipeline.codec.JsonCodecs.given
import io.circe.syntax.*
import zio.*
import zio.aws.kinesis.Kinesis
import zio.aws.kinesis.model.*
import java.nio.ByteBuffer
import java.util.UUID

trait KinesisProducer:
  def publish(interval: PerformanceInterval): Task[Unit]

object KinesisProducer:
  val live: ZLayer[Kinesis & KinesisConfig, Nothing, KinesisProducer] =
    ZLayer.fromFunction { (kinesis: Kinesis, cfg: KinesisConfig) =>
      new KinesisProducer:
        def publish(interval: PerformanceInterval): Task[Unit] =
          kinesis.putRecord(
            PutRecordRequest(
              streamName   = cfg.outputStreamName,
              data         = zio.Chunk.fromArray(interval.asJson.noSpaces.getBytes("UTF-8")),
              partitionKey = interval.dispatchUnit
            )
          ).mapError(_.toThrowable).unit
    }
```

### Test de integración Kinesis

```scala
// test/infrastructure/kinesis/KinesisIntegrationSpec.scala
package com.pipeline.infrastructure.kinesis

import zio.test.*
import zio.*
import com.pipeline.domain.*
import java.time.Instant

object KinesisIntegrationSpec extends ZIOSpecDefault:
  // Requiere LocalStack levantado
  def spec = suite("Kinesis Integration")(
    test("publica y consume un PerformanceInterval") {
      for
        producer  <- ZIO.service[KinesisProducer]
        interval   = PerformanceInterval("du-test", "node-1", Instant.now(), meteredValue = Some(1000.0))
        _         <- producer.publish(interval)
        // Verificar manualmente con AWS CLI o consumer test
      yield assertCompletes
    }
  ).provide(KinesisProducer.live, /* layers de config y Kinesis */)
```

---

## 2. DynamoDB — AuditRepository

### `infrastructure/dynamo/AuditRepository.scala`

```scala
package com.pipeline.infrastructure.dynamo

import com.pipeline.domain.AuditRecord
import org.scanamo.*
import org.scanamo.syntax.*
import org.scanamo.zio.*
import zio.*
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

trait AuditRepository:
  def save(record: AuditRecord): Task[Unit]

object AuditRepository:
  val live: ZLayer[DynamoDbAsyncClient & String, Nothing, AuditRepository] =
    ZLayer.fromFunction { (client: DynamoDbAsyncClient, tableName: String) =>
      val scanamo = ScanamoZio(client)
      val table   = Table[AuditRecord](tableName)

      new AuditRepository:
        def save(record: AuditRecord): Task[Unit] =
          scanamo.exec(table.put(record))
            .mapError(e => new RuntimeException(s"DynamoDB put failed: $e"))
            .unit
    }
```

### Formato del ítem DynamoDB

| Atributo | Tipo | Descripción |
|---|---|---|
| `nodeId` | String (PK) | Identificador del nodo |
| `dttmUtc` | String (SK) | Timestamp ISO-8601 |
| `registrationId` | String? | Opcional |
| `baselineId` | String? | Opcional |
| `receivedAt` | String | Timestamp de recepción |
| `ttl` | Number | Epoch seconds = dttm_utc + 24h |

### Test de integración DynamoDB

```scala
object AuditRepositorySpec extends ZIOSpecDefault:
  def spec = suite("AuditRepository")(
    test("guarda un AuditRecord y TTL es dttm_utc + 24h") {
      val now = java.time.Instant.now()
      val msg = InputMessage("node-1", now, Some("reg-001"), None)
      val record = AuditRecord.from(msg, now)
      for
        repo <- ZIO.service[AuditRepository]
        _    <- repo.save(record)
        expectedTtl = now.plusSeconds(86400).getEpochSecond
      yield assertTrue(record.ttl == expectedTtl)
    }
  ).provide(AuditRepository.live, /* dynamo client layer */)
```

---

## 3. Redis — PerformanceCache

### `infrastructure/redis/PerformanceCache.scala`

```scala
package com.pipeline.infrastructure.redis

import com.pipeline.domain.PerformanceInterval
import com.pipeline.codec.JsonCodecs.given
import io.circe.syntax.*
import io.circe.parser.decode
import zio.*
import zio.redis.*
import java.time.Instant

trait PerformanceCache:
  def upsert(interval: PerformanceInterval): Task[PerformanceInterval]

object PerformanceCache:
  val live: ZLayer[Redis, Nothing, PerformanceCache] =
    ZLayer.fromFunction { (redis: Redis) =>
      new PerformanceCache:
        def upsert(interval: PerformanceInterval): Task[PerformanceInterval] =
          for
            existing <- redis
                          .get(interval.dispatchUnit)
                          .returning[String]
                          .map(_.flatMap(s => decode[PerformanceInterval](s).toOption))
            merged    = existing.fold(interval)(_.merge(interval))
            _        <- redis.set(interval.dispatchUnit, merged.asJson.noSpaces)
          yield merged
    }
```

### Estrategia de Merge en Redis

```
Estado previo en Redis (dispatch_unit = "DU-42"):
  { meteredValue: 1000.5, baselineValue: null }

Nuevo registro entrante:
  { meteredValue: null, baselineValue: 950.3, baselineId: "B-7" }

Resultado del merge (devuelto y guardado):
  { meteredValue: 1000.5, baselineValue: 950.3, baselineId: "B-7" }
```

### Test de integración Redis

```scala
object PerformanceCacheSpec extends ZIOSpecDefault:
  def spec = suite("PerformanceCache")(
    test("upsert acumula meteredValue y baselineValue de lotes distintos") {
      val now = Instant.now()
      val first  = PerformanceInterval("DU-42", "n1", now, meteredValue = Some(1000.5))
      val second = PerformanceInterval("DU-42", "n1", now, baselineValue = Some(950.3), baselineId = Some("B-7"))
      for
        cache   <- ZIO.service[PerformanceCache]
        _       <- cache.upsert(first)
        result  <- cache.upsert(second)
      yield
        assertTrue(result.meteredValue.contains(1000.5)) &&
        assertTrue(result.baselineValue.contains(950.3)) &&
        assertTrue(result.baselineId.contains("B-7"))
    }
  ).provide(PerformanceCache.live, /* redis layer */)
```

---

## 4. PostgreSQL — PerformanceRepository

### Schema SQL

```sql
-- infra/sql/V1__create_performance_intervals.sql
CREATE TABLE IF NOT EXISTS performance_intervals (
  dispatch_unit   VARCHAR(255)  NOT NULL,
  node_id         VARCHAR(255)  NOT NULL,
  dttm_utc        TIMESTAMPTZ   NOT NULL,
  metered_value   DOUBLE PRECISION,
  baseline_value  DOUBLE PRECISION,
  baseline_id     VARCHAR(255),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
  PRIMARY KEY (dispatch_unit, dttm_utc)
);

CREATE INDEX idx_pi_node_id    ON performance_intervals(node_id);
CREATE INDEX idx_pi_updated_at ON performance_intervals(updated_at DESC);
```

### `infrastructure/postgres/PerformanceRepository.scala`

```scala
package com.pipeline.infrastructure.postgres

import com.pipeline.domain.PerformanceInterval
import com.pipeline.config.PostgresConfig
import slick.jdbc.PostgresProfile.api.*
import zio.*
import java.time.Instant

class PerformanceIntervals(tag: Tag) extends Table[PerformanceInterval](tag, "performance_intervals"):
  def dispatchUnit  = column[String]("dispatch_unit")
  def nodeId        = column[String]("node_id")
  def dttmUtc       = column[Instant]("dttm_utc")
  def meteredValue  = column[Option[Double]]("metered_value")
  def baselineValue = column[Option[Double]]("baseline_value")
  def baselineId    = column[Option[String]]("baseline_id")
  def pk            = primaryKey("pk_pi", (dispatchUnit, dttmUtc))
  def *             = (dispatchUnit, nodeId, dttmUtc, meteredValue, baselineValue, baselineId)
                        .mapTo[PerformanceInterval]

trait PerformanceRepository:
  def upsert(interval: PerformanceInterval): Task[Unit]

object PerformanceRepository:
  val live: ZLayer[PostgresConfig, Throwable, PerformanceRepository] =
    ZLayer.fromZIO {
      ZIO.service[PostgresConfig].map { cfg =>
        val db = Database.forURL(
          s"jdbc:postgresql://${cfg.host}:${cfg.port}/${cfg.database}",
          user     = cfg.user,
          password = cfg.password,
          driver   = "org.postgresql.Driver"
        )
        val table = TableQuery[PerformanceIntervals]

        new PerformanceRepository:
          def upsert(interval: PerformanceInterval): Task[Unit] =
            ZIO.fromFuture { _ =>
              db.run(
                table.insertOrUpdate(interval)
              )
            }.unit
      }
    }
```

---

## 5. Configuración de Layers ZIO

Organizar los layers para facilitar la inyección de dependencias:

```scala
// infrastructure/Layers.scala
package com.pipeline.infrastructure

import zio.*
// importar todos los live layers

object Layers:
  val postgres = PerformanceRepository.live
  val dynamo   = AuditRepository.live
  val redis    = PerformanceCache.live
  val kinesis  = KinesisProducer.live ++ KinesisConsumer.live

  val all = postgres ++ dynamo ++ redis ++ kinesis
```

---

## Criterios de Aceptación

- [ ] `KinesisProducer.publish` escribe mensajes al output-stream de LocalStack sin errores
- [ ] `AuditRepository.save` persiste registros en DynamoDB con TTL correcto
- [ ] `PerformanceCache.upsert` acumula campos de lotes distintos correctamente
- [ ] `PerformanceRepository.upsert` realiza INSERT OR UPDATE en PostgreSQL
- [ ] Todos los tests de integración pasan con los servicios Docker levantados
- [ ] `sbt test` no requiere servicios externos para tests unitarios (mocks/stubs)
