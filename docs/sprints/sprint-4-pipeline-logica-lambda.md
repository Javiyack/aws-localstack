# Sprint 4 — Pipeline: Lógica de Negocio & Lambda Handler

## Objetivo

Implementar el flujo de procesamiento completo, el handler AWS Lambda, y verificar el pipeline end-to-end funcionando sobre LocalStack.

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 3 completado (todas las integraciones operativas)

---

## 1. HTTP Client — ValueClient

### `infrastructure/http/ValueClient.scala`

```scala
package com.pipeline.infrastructure.http

import com.pipeline.config.ValueBackendConfig
import io.circe.*
import io.circe.generic.semiauto.*
import sttp.client3.*
import sttp.client3.circe.*
import zio.*

private case class ValueResponse(value: Double)
private given Decoder[ValueResponse] = deriveDecoder

trait ValueClient:
  def getValue(id: String): Task[Double]

object ValueClient:
  val live: ZLayer[SttpBackend[Task, Any] & ValueBackendConfig, Nothing, ValueClient] =
    ZLayer.fromFunction { (backend: SttpBackend[Task, Any], cfg: ValueBackendConfig) =>
      new ValueClient:
        def getValue(id: String): Task[Double] =
          val request = basicRequest
            .get(uri"${cfg.baseUrl}/$id")
            .response(asJson[ValueResponse])

          backend.send(request).flatMap { response =>
            response.body match
              case Right(body)  => ZIO.succeed(body.value)
              case Left(error)  => ZIO.fail(new RuntimeException(s"ValueBackend error for id=$id: $error"))
          }.retry(Schedule.exponentialBackoff(100.millis) && Schedule.recurs(3))
    }
```

### Test con mock server

```scala
// test/infrastructure/http/ValueClientSpec.scala
object ValueClientSpec extends ZIOSpecDefault:
  def spec = suite("ValueClient")(
    test("obtiene un Double de la respuesta JSON") {
      // Usar sttp mock backend o testcontainer stub
      assertCompletes
    },
    test("reintenta 3 veces en caso de error 5xx") {
      assertCompletes
    },
    test("falla con error descriptico si el body es inválido") {
      assertCompletes
    }
  )
```

---

## 2. Orquestador del Pipeline

### `application/PipelineProcessor.scala`

```scala
package com.pipeline.application

import com.pipeline.domain.*
import com.pipeline.infrastructure.dynamo.AuditRepository
import com.pipeline.infrastructure.http.ValueClient
import com.pipeline.infrastructure.kinesis.KinesisProducer
import com.pipeline.infrastructure.postgres.PerformanceRepository
import com.pipeline.infrastructure.redis.PerformanceCache
import zio.*
import java.time.Instant

trait PipelineProcessor:
  def process(message: InputMessage): Task[Unit]

object PipelineProcessor:
  val live: ZLayer[
    AuditRepository & ValueClient & PerformanceCache & PerformanceRepository & KinesisProducer,
    Nothing,
    PipelineProcessor
  ] =
    ZLayer.fromFunction {
      (
        audit:      AuditRepository,
        httpClient: ValueClient,
        cache:      PerformanceCache,
        db:         PerformanceRepository,
        kinesis:    KinesisProducer
      ) =>
        new PipelineProcessor:
          def process(message: InputMessage): Task[Unit] =
            for
              now      <- ZIO.clockWith(_.instant)

              // 1. Validar mensaje
              _        <- ZIO.fail(new IllegalArgumentException(s"Invalid message: $message"))
                            .unless(message.isValid)

              // 2. Auditoría en DynamoDB
              _        <- audit.save(AuditRecord.from(message, now))

              // 3. Llamada al Value Backend
              id       <- ZIO.fromOption(message.resolvedId)
                            .orElseFail(new RuntimeException("No id present"))
              value    <- httpClient.getValue(id)

              // 4. Construir PerformanceInterval según el tipo de mensaje
              interval  = if message.isRegistration then
                            PerformanceInterval(
                              dispatchUnit = id,  // TODO: obtener dispatch_unit del contexto/mensaje
                              nodeId       = message.nodeId,
                              dttmUtc      = message.dttmUtc,
                              meteredValue = Some(value)
                            )
                          else
                            PerformanceInterval(
                              dispatchUnit  = id,
                              nodeId        = message.nodeId,
                              dttmUtc       = message.dttmUtc,
                              baselineValue = Some(value),
                              baselineId    = message.baselineId
                            )

              // 5. Upsert en Redis → obtener objeto merged
              merged   <- cache.upsert(interval)

              // 6. Persistir en PostgreSQL
              _        <- db.upsert(merged)

              // 7. Publicar al output stream de Kinesis
              _        <- kinesis.publish(merged)
            yield ()
    }
```

> **Nota sobre `dispatch_unit`:** el campo no está en el esquema de `InputMessage` del README. Asumir que se deriva de `registration_id` o `baseline_id`, o añadir el campo al mensaje de entrada si el requisito se clarifica.

---

## 3. Procesamiento de Lotes

### `application/BatchProcessor.scala`

```scala
package com.pipeline.application

import zio.*
import zio.stream.ZStream
import com.pipeline.domain.InputMessage

trait BatchProcessor:
  def processBatch(messages: List[InputMessage]): Task[BatchResult]

final case class BatchResult(
  succeeded: Int,
  failed:    Int,
  errors:    List[String]
)

object BatchProcessor:
  val live: ZLayer[PipelineProcessor, Nothing, BatchProcessor] =
    ZLayer.fromFunction { (processor: PipelineProcessor) =>
      new BatchProcessor:
        def processBatch(messages: List[InputMessage]): Task[BatchResult] =
          ZIO.foreach(messages) { msg =>
            processor.process(msg)
              .as(Right(()))
              .catchAll(err => ZIO.succeed(Left(s"${msg.nodeId}: ${err.getMessage}")))
          }.map { results =>
            val errors    = results.collect { case Left(e) => e }
            val succeeded = results.count(_.isRight)
            BatchResult(succeeded, errors.size, errors)
          }
    }
```

---

## 4. AWS Lambda Handler

### `LambdaHandler.scala`

```scala
package com.pipeline

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.pipeline.application.{BatchProcessor, PipelineProcessor}
import com.pipeline.codec.JsonCodecs.given
import com.pipeline.config.AppConfig
import com.pipeline.domain.InputMessage
import com.pipeline.infrastructure.Layers
import io.circe.parser.decode
import zio.*
import zio.Runtime.default as runtime
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

class LambdaHandler extends RequestHandler[KinesisEvent, Unit]:

  // El runtime ZIO se inicializa una sola vez (warm start)
  private val appLayer = AppConfig.layer >>> Layers.all >>> BatchProcessor.live

  override def handleRequest(event: KinesisEvent, context: Context): Unit =
    val messages: List[InputMessage] = event.getRecords.asScala.toList.flatMap { record =>
      val json = StandardCharsets.UTF_8.decode(record.getKinesis.getData).toString
      decode[InputMessage](json) match
        case Right(msg) => Some(msg)
        case Left(err)  =>
          context.getLogger.log(s"[WARN] Failed to decode message: $err | json: $json")
          None
    }

    context.getLogger.log(s"[INFO] Processing batch of ${messages.size} messages")

    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(
        ZIO.serviceWithZIO[BatchProcessor](_.processBatch(messages))
          .tap(result => ZIO.logInfo(s"Batch complete: ${result.succeeded} ok, ${result.failed} failed"))
          .provide(appLayer)
      ).getOrThrow()
    }
```

---

## 5. Empaquetado con sbt-assembly

```bash
cd pipeline
sbt assembly
# Output: target/scala-3.x/pipeline-assembly.jar
```

Verificar el JAR:
```bash
ls -lh target/scala-3.x/pipeline-assembly.jar
```

---

## 6. Deploy en LocalStack

Actualizar `infra/main.tf` con la ruta real del JAR y re-aplicar:

```bash
cd infra
terraform apply -auto-approve
```

---

## 7. Test End-to-End

### Publicar un mensaje de prueba al input-stream

```bash
# Mensaje con registration_id
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
aws --endpoint-url=http://localhost:4566 kinesis put-record \
  --stream-name input-stream \
  --partition-key "node-1" \
  --data "$(echo '{"nodeId":"node-1","dttmUtc":"2026-03-20T10:00:00Z","registrationId":"reg-001"}' | base64)"
```

### Verificar el output-stream

```bash
# Obtener shard iterator
ITERATOR=$(AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
  aws --endpoint-url=http://localhost:4566 kinesis get-shard-iterator \
  --stream-name output-stream \
  --shard-id shardId-000000000000 \
  --shard-iterator-type TRIM_HORIZON \
  --query 'ShardIterator' --output text)

# Leer registros
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
aws --endpoint-url=http://localhost:4566 kinesis get-records \
  --shard-iterator "$ITERATOR" \
  --query 'Records[*].Data' --output text | base64 -d
```

### Verificar DynamoDB

```bash
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
aws --endpoint-url=http://localhost:4566 dynamodb scan \
  --table-name audit-records \
  --limit 5
```

### Verificar PostgreSQL

```bash
psql -h localhost -U pipeline -d pipeline -c \
  "SELECT * FROM performance_intervals ORDER BY updated_at DESC LIMIT 5;"
```

---

## 8. Logging Estructurado

Configurar ZIO Logging para CloudWatch:

```scala
// En LambdaHandler: usar ZIO.logInfo / ZIO.logError / ZIO.logWarning
// Añadir correlación de lote: ZIO.logAnnotate("batchSize", messages.size.toString)

ZIO.logInfo("Processing batch") @@ ZIOAspect.annotated("batchSize", messages.size.toString)
```

---

## Criterios de Aceptación

- [ ] `sbt assembly` genera el JAR sin errores
- [ ] `terraform apply` despliega la Lambda en LocalStack
- [ ] Un mensaje publicado al input-stream genera el output en el output-stream
- [ ] El registro de auditoría aparece en DynamoDB con TTL correcto
- [ ] El `performance_interval` aparece en PostgreSQL
- [ ] Los logs de la Lambda son visibles en CloudWatch
- [ ] Un mensaje inválido (sin `registrationId` ni `baselineId`) es registrado como error y no detiene el lote
- [ ] Test con lote de 500 mensajes procesados sin errores
