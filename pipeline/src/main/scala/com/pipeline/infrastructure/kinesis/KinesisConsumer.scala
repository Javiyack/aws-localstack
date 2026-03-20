package com.pipeline.infrastructure.kinesis

import com.pipeline.config.AppConfig
import com.pipeline.domain.InputMessage
import com.pipeline.codec.JsonCodecs.given
import io.circe.parser.decode
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import software.amazon.awssdk.services.kinesis.model.*
import zio.*
import zio.stream.*

import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.jdk.CollectionConverters.*

/** Consume registros de un stream Kinesis.
 *
 *  Lee los shards disponibles y emite mensajes decodificados como ZStream.
 *  Los registros que fallen la decodificación JSON se registran y se descartan
 *  (dead-letter semántico a nivel de log) para no bloquear el stream.
 */
object KinesisConsumer:

  type Env = KinesisAsyncClient & AppConfig

  /** Stream de InputMessage leídos desde el stream de entrada configurado.
   *
   *  @param fromTimestamp  punto de inicio para TRIM_HORIZON si None,
   *                        o AT_TIMESTAMP si Some.
   */
  def stream(
    fromTimestamp: Option[Instant] = None
  ): ZStream[Env, Throwable, InputMessage] =
    ZStream.serviceWithStream[AppConfig] { cfg =>
      shardsStream(cfg.kinesis.inputStreamName, fromTimestamp)
        .via(decodeRecords)
    }

  // ─── private helpers ──────────────────────────────────────────────────────

  private def shardsStream(
    streamName: String,
    fromTimestamp: Option[Instant]
  ): ZStream[KinesisAsyncClient, Throwable, Record] =
    ZStream.serviceWithStream[KinesisAsyncClient] { client =>
      ZStream
        .fromZIO(listShards(client, streamName))
        .flatMap(ZStream.fromIterable)
        .flatMap { shardId =>
          recordsForShard(client, streamName, shardId, fromTimestamp)
        }
    }

  private def listShards(
    client: KinesisAsyncClient,
    streamName: String
  ): Task[List[String]] =
    ZIO
      .fromCompletableFuture(
        client.listShards(
          ListShardsRequest.builder().streamName(streamName).build()
        )
      )
      .map(_.shards().asScala.map(_.shardId()).toList)

  private def recordsForShard(
    client: KinesisAsyncClient,
    streamName: String,
    shardId: String,
    fromTimestamp: Option[Instant]
  ): ZStream[Any, Throwable, Record] =
    ZStream.unwrap(
      for
        iteratorType <- ZIO.succeed(
          fromTimestamp.fold(ShardIteratorType.TRIM_HORIZON)(_ => ShardIteratorType.AT_TIMESTAMP)
        )
        reqBuilder = GetShardIteratorRequest
          .builder()
          .streamName(streamName)
          .shardId(shardId)
          .shardIteratorType(iteratorType)
        reqBuilderWithTs = fromTimestamp.fold(reqBuilder)(ts => reqBuilder.timestamp(ts))
        iterResp <- ZIO.fromCompletableFuture(client.getShardIterator(reqBuilderWithTs.build()))
        iterator  = iterResp.shardIterator()
      yield pollRecords(client, iterator)
    )

  private def pollRecords(
    client: KinesisAsyncClient,
    initialIterator: String
  ): ZStream[Any, Throwable, Record] =
    ZStream.unfoldZIO(initialIterator) { iterator =>
      ZIO
        .fromCompletableFuture(
          client.getRecords(GetRecordsRequest.builder().shardIterator(iterator).limit(100).build())
        )
        .flatMap { resp =>
          val records     = resp.records().asScala.toList
          val nextIterOpt = Option(resp.nextShardIterator()).filter(_.nonEmpty)
          nextIterOpt match
            case None           => ZIO.succeed(None)
            case Some(nextIter) =>
              (if records.isEmpty then ZIO.sleep(1.second) else ZIO.unit)
                .as(Some(records -> nextIter))
        }
    }.flatMap(ZStream.fromIterable)

  private val decodeRecords: ZPipeline[Any, Throwable, Record, InputMessage] =
    ZPipeline.mapZIO { (record: Record) =>
      val raw = StandardCharsets.UTF_8.decode(record.data().asByteBuffer()).toString
      ZIO
        .fromEither(decode[InputMessage](raw))
        .tapError(e => ZIO.logWarning(s"Descartando registro inválido: $e | raw=$raw"))
        .option
    }.collect { case Some(msg) => msg }
