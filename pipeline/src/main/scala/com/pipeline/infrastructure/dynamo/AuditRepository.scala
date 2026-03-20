package com.pipeline.infrastructure.dynamo

import com.pipeline.config.AppConfig
import com.pipeline.domain.AuditRecord
import org.scanamo.*
import org.scanamo.syntax.*
import org.scanamo.generic.auto.given
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import zio.*

import java.time.Instant
import scala.concurrent.ExecutionContext

/** Persiste y consulta AuditRecord en la tabla DynamoDB de auditoría.
 *
 *  Usa Scanamo para mapear automáticamente la case class ↔ AttributeValue.
 *  La tabla tiene PK = dispatchUnit (S) + SK = dttmUtc (S ISO-8601).
 *  TTL se gestiona mediante el atributo `ttl` (N epoch seconds).
 */
object AuditRepository:

  type Env = DynamoDbAsyncClient & AppConfig

  /** Escribe un AuditRecord. Sobrescribe si ya existía (idempotente). */
  def put(record: AuditRecord): ZIO[Env, Throwable, Unit] =
    withScanamo { (scanamo, table) =>
      ZIO.fromFuture(_ => scanamo.exec(table.put(record))).unit
    }

  /** Escribe varios registros en paralelo (sin garantía de orden). */
  def putAll(records: List[AuditRecord]): ZIO[Env, Throwable, Unit] =
    ZIO.foreachPar(records)(put).unit

  /** Obtiene un registro por PK compuesta. */
  def get(
    dispatchUnit: String,
    dttmUtc: Instant
  ): ZIO[Env, Throwable, Option[AuditRecord]] =
    withScanamo { (scanamo, table) =>
      ZIO
        .fromFuture(_ =>
          scanamo.exec(
            table.get("dispatchUnit" === dispatchUnit and "dttmUtc" === dttmUtc.toString)
          )
        )
        .flatMap {
          case None            => ZIO.none
          case Some(Right(r))  => ZIO.some(r)
          case Some(Left(err)) => ZIO.fail(new RuntimeException(err.toString))
        }
    }

  /** Consulta todos los registros de una unidad en un período. */
  def query(
    dispatchUnit: String,
    from: Instant,
    to: Instant
  ): ZIO[Env, Throwable, List[AuditRecord]] =
    withScanamo { (scanamo, table) =>
      ZIO
        .fromFuture(_ =>
          scanamo.exec(
            table.query(
              "dispatchUnit" === dispatchUnit and
                ("dttmUtc" between from.toString and to.toString)
            )
          )
        )
        .flatMap { results =>
          ZIO.foreach(results) {
            case Right(r) => ZIO.succeed(r)
            case Left(e)  => ZIO.fail(new RuntimeException(e.toString))
          }
        }
    }

  // ─── private ──────────────────────────────────────────────────────────────

  private def withScanamo[A](
    f: (ScanamoAsync, Table[AuditRecord]) => ZIO[Any, Throwable, A]
  ): ZIO[Env, Throwable, A] =
    for
      cfg    <- ZIO.service[AppConfig]
      client <- ZIO.service[DynamoDbAsyncClient]
      result <- ZIO
                  .attempt {
                    given ExecutionContext = ExecutionContext.global
                    ScanamoAsync(client)
                  }
                  .flatMap(scanamo => f(scanamo, Table[AuditRecord](cfg.dynamo.tableName)))
    yield result
