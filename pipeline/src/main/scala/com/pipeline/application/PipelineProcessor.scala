package com.pipeline.application

import com.pipeline.config.AppConfig
import com.pipeline.domain.*
import com.pipeline.infrastructure.dynamo.AuditRepository
import com.pipeline.infrastructure.http.ValueClient
import com.pipeline.infrastructure.kinesis.KinesisProducer
import com.pipeline.infrastructure.postgres.PerformanceRepository
import com.pipeline.infrastructure.redis.PerformanceCache
import slick.jdbc.PostgresProfile.api.Database
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import sttp.client3.SttpBackend
import zio.*
import zio.redis.Redis

import java.time.Instant

/** Orquesta el procesamiento completo de un InputMessage.
 *
 *  Pasos:
 *   1. Validar el mensaje
 *   2. Determinar el identificador (registration vs baseline)
 *   3. Llamar al value-backend para obtener el valor numérico
 *   4. Construir el PerformanceInterval
 *   5. Hacer upsert en Redis (acumulación caliente)
 *   6. Persistir en PostgreSQL (histórico frío)
 *   7. Registrar en DynamoDB (auditoría)
 *   8. Publicar en el stream de salida (fan-out)
 */
object PipelineProcessor:

  type Env =
    AppConfig &
    KinesisAsyncClient &
    DynamoDbAsyncClient &
    Redis &
    Database &
    SttpBackend[Task, Any]

  final case class ProcessingResult(
    message:  InputMessage,
    interval: PerformanceInterval,
    value:    Double
  )

  /** Procesa un único mensaje.
   *
   *  Falla con un Throwable descriptivo si algún paso falla.
   *  El llamador (LambdaHandler / BatchProcessor) decide si enviar a DLQ.
   */
  def process(msg: InputMessage): ZIO[Env, Throwable, ProcessingResult] =
    for
      // 1 — validación
      _   <- ZIO.fail(new IllegalArgumentException(s"Mensaje inválido: $msg"))
                .when(!msg.isValid)

      // 2 — identificador efectivo
      id  <- ZIO.fromOption(msg.resolvedId)
                .orElseFail(new IllegalArgumentException("Sin ID"))

      // 3 — valor del servicio externo
      _   <- ZIO.logInfo(s"Obteniendo valor para id=$id nodeId=${msg.nodeId}")
      v   <- ValueClient.getValue(id)

      // 4 — construir intervalo
      now  = Instant.now()
      pi   = PerformanceInterval(
               dispatchUnit  = msg.nodeId,
               nodeId        = msg.nodeId,
               dttmUtc       = msg.dttmUtc,
               meteredValue  = if msg.isRegistration then Some(v) else None,
               baselineValue = if msg.isRegistration then None else Some(v),
               baselineId    = msg.baselineId
             )

      // 5 — upsert Redis
      merged <- PerformanceCache.upsert(pi)
      _      <- ZIO.logDebug(s"Redis upsert: $merged")

      // 6 — persistir PostgreSQL
      _ <- PerformanceRepository.upsert(merged)

      // 7 — auditoría DynamoDB
      _ <- AuditRepository.put(AuditRecord.from(msg, now))

      // 8 — publicar en stream de salida
      _ <- KinesisProducer.publish(msg)

      _ <- ZIO.logInfo(s"Procesado: nodeId=${msg.nodeId} value=$v")
    yield ProcessingResult(msg, merged, v)

  /** Procesa una lista de mensajes aislando fallos individuales. */
  def processBatch(
    msgs: List[InputMessage]
  ): ZIO[Env, Nothing, List[Either[Throwable, ProcessingResult]]] =
    ZIO.foreach(msgs) { msg =>
      process(msg).either.tap {
        case Left(e)  => ZIO.logError(s"Error procesando ${msg.nodeId}: ${e.getMessage}")
        case Right(_) => ZIO.unit
      }
    }
