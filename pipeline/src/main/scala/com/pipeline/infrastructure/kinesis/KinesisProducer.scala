package com.pipeline.infrastructure.kinesis

import com.pipeline.config.AppConfig
import com.pipeline.domain.InputMessage
import com.pipeline.codec.JsonCodecs.given
import io.circe.syntax.*
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.*
import zio.*

import java.nio.charset.StandardCharsets

/** Publica registros en el stream de salida Kinesis. */
object KinesisProducer:

  type Env = KinesisAsyncClient & AppConfig

  /** Publica un InputMessage serializado como JSON.
   *
   *  Usa `dispatchUnit` como partition key para garantizar orden por unidad.
   */
  def publish(msg: InputMessage): ZIO[Env, Throwable, PutRecordResponse] =
    for
      cfg  <- ZIO.service[AppConfig]
      resp <- put(msg, cfg.kinesis.outputStream)
    yield resp

  /** Publica un lote de mensajes (PutRecords — hasta 500 por llamada). */
  def publishBatch(
    msgs: List[InputMessage]
  ): ZIO[Env, Throwable, Unit] =
    ZIO.serviceWithZIO[AppConfig] { cfg =>
      ZIO
        .foreach(msgs.grouped(500).toList) { batch =>
          val entries = batch.map { msg =>
            val json = msg.asJson.noSpaces
            PutRecordsRequestEntry
              .builder()
              .data(SdkBytes.fromString(json, StandardCharsets.UTF_8))
              .partitionKey(msg.dispatchUnit)
              .build()
          }
          ZIO
            .fromCompletableFuture(
              summon[KinesisAsyncClient].putRecords(   // resolved via ZIO.serviceWith below
                PutRecordsRequest
                  .builder()
                  .streamName(cfg.kinesis.outputStream)
                  .records(entries*)
                  .build()
              )
            )
            .unit
        }
        .unit
    }

  // ─── private ──────────────────────────────────────────────────────────────

  private def put(
    msg: InputMessage,
    streamName: String
  ): ZIO[KinesisAsyncClient, Throwable, PutRecordResponse] =
    ZIO.serviceWithZIO[KinesisAsyncClient] { client =>
      val json = msg.asJson.noSpaces
      ZIO.fromCompletableFuture(
        client.putRecord(
          PutRecordRequest
            .builder()
            .streamName(streamName)
            .data(SdkBytes.fromString(json, StandardCharsets.UTF_8))
            .partitionKey(msg.dispatchUnit)
            .build()
        )
      )
    }
