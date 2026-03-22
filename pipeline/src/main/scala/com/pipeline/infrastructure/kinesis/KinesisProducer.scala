package com.pipeline.infrastructure.kinesis

import com.pipeline.config.AppConfig
import com.pipeline.codec.JsonCodecs.given
import io.circe.syntax.*
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.*
import zio.*

import java.nio.charset.StandardCharsets
import com.pipeline.domain.PerformanceInterval

/** Publica registros en el stream de salida Kinesis. */
object KinesisProducer:

  type Env = KinesisAsyncClient & AppConfig

  /** Publica un PerformanceInterval serializado como JSON.
   *
   *  Usa `dispatchUnit` como partition key para garantizar orden por unidad.
   */
  def publish(msg: PerformanceInterval): ZIO[Env, Throwable, PutRecordResponse] =
    for
      cfg  <- ZIO.service[AppConfig]
      resp <- put(msg, cfg.kinesis.outputStreamName)
    yield resp

  /** Publica un lote de mensajes (PutRecords — hasta 500 por llamada). */
  def publishBatch(
    msgs: List[PerformanceInterval]
  ): ZIO[Env, Throwable, Unit] =
    for
      cfg    <- ZIO.service[AppConfig]
      client <- ZIO.service[KinesisAsyncClient]
      _      <- ZIO.foreach(msgs.grouped(500).toList) { batch =>
                  val entries = batch.map { msg =>
                    val json = msg.asJson.noSpaces
                    PutRecordsRequestEntry
                      .builder()
                      .data(SdkBytes.fromString(json, StandardCharsets.UTF_8))
                      .partitionKey(msg.nodeId)
                      .build()
                  }
                  ZIO
                    .fromCompletableFuture(
                      client.putRecords(
                        PutRecordsRequest
                          .builder()
                          .streamName(cfg.kinesis.outputStreamName)
                          .records(entries*)
                          .build()
                      )
                    )
                    .unit
                }
    yield ()

  // ─── private ──────────────────────────────────────────────────────────────

  private def put(
    msg: PerformanceInterval,
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
            .partitionKey(msg.nodeId)
            .build()
        )
      )
    }
