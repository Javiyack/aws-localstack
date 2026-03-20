package com.pipeline

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.pipeline.application.PipelineProcessor
import com.pipeline.config.AppConfig
import com.pipeline.layers.AppLayers
import com.pipeline.codec.JsonCodecs.given
import io.circe.parser.decode
import zio.*
import zio.logging.backend.SLF4J

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

/** Punto de entrada AWS Lambda.
 *
 *  AWS itera sobre todos los registros del batch Kinesis y llama a
 *  [[handleRequest]] una vez por invocación.  Aquí procesamos el batch
 *  completo aislando fallos individuales: un registro fallido no bloquea
 *  los demás; si el error persiste se enviará a la DLQ configurada en
 *  Terraform.
 *
 *  La instancia del Runtime ZIO se crea una sola vez (campo `runtime`)
 *  aprovechando el warm-start de Lambda.
 */
class LambdaHandler extends RequestHandler[KinesisEvent, Unit]:

  /** Runtime ZIO inicializado una vez por container (warm start). */
  private val runtime: Runtime[PipelineProcessor.Env] =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe
        .fromLayer(
          Runtime.removeDefaultLoggers >>>
          SLF4J.slf4j >>>
          AppLayers.live
        )
    }

  override def handleRequest(event: KinesisEvent, context: Context): Unit =
    val records = event.getRecords.asScala.toList

    val program: ZIO[PipelineProcessor.Env, Nothing, Unit] =
      for
        _ <- ZIO.logInfo(s"Lambda invocada: ${records.size} registros")

        msgs = records.flatMap { rec =>
          val raw = StandardCharsets.UTF_8
            .decode(rec.getKinesis.getData)
            .toString
          decode[com.pipeline.domain.InputMessage](raw) match
            case Right(m) => Some(m)
            case Left(e) =>
              java.lang.System.err.println(s"Record inválido descartado: $e")
              None
        }

        results <- PipelineProcessor.processBatch(msgs)

        successes = results.count(_.isRight)
        failures  = results.count(_.isLeft)
        _ <- ZIO.logInfo(s"Batch completado: $successes ok / $failures fallos")

        // Si todos los registros fallaron, propagamos el error para que
        // Lambda reintente y eventualmente envíe a DLQ.
        _ <- ZIO.fail(new RuntimeException("Todos los registros fallaron"))
               .when(msgs.nonEmpty && failures == msgs.size)
               .orDie
      yield ()

    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(program).getOrThrowFiberFailure()
    }
