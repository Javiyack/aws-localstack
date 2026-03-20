package com.pipeline.infrastructure.dynamo

import com.pipeline.config.AppConfig
import com.pipeline.domain.AuditRecord
import org.scanamo.*
import org.scanamo.syntax.*
import org.scanamo.generic.auto.given
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import zio.*

import java.time.Instant

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
    withTable { table =>
      ZIO.fromFuture(_ => table.put(record).execute() )
    }.unit

  /** Escribe varios registros en paralelo (sin garantía de orden). */
  def putAll(records: List[AuditRecord]): ZIO[Env, Throwable, Unit] =
    ZIO.foreachPar(records)(put).unit

  /** Obtiene un registro por PK compuesta. */
  def get(
    dispatchUnit: String,
    dttmUtc: Instant
  ): ZIO[Env, Throwable, Option[AuditRecord]] =
    withTable { table =>
      ZIO.fromFuture(_ =>
        table
          .get("dispatchUnit" === dispatchUnit and "dttmUtc" === dttmUtc.toString)
          .execute()
      )
    }.flatMap {
      case None         => ZIO.none
      case Some(result) =>
        ZIO.fromEither(result.left.map(e => new RuntimeException(e.toString))).map(Some(_))
    }

  /** Consulta todos los registros de una unidad en un período.
   *
   *  @param from  instante inicial (inclusive)
   *  @param to    instante final   (inclusive)
   */
  def query(
    dispatchUnit: String,
    from: Instant,
    to: Instant
  ): ZIO[Env, Throwable, List[AuditRecord]] =
    withTable { table =>
      ZIO.fromFuture(_ =>
        table
          .query(
            "dispatchUnit" === dispatchUnit and
              ("dttmUtc" between from.toString and to.toString)
          )
          .execute()
      )
    }.flatMap { results =>
      ZIO.foreach(results) {
        case Right(r) => ZIO.succeed(r)
        case Left(e)  => ZIO.fail(new RuntimeException(e.toString))
      }
    }

  // ─── private ──────────────────────────────────────────────────────────────

  private type ScanamoTable = ScanamoAsync#Table[AuditRecord]

  private def withTable[A](
    f: ScanamoTable => ZIO[Any, Throwable, A]
  ): ZIO[Env, Throwable, A] =
    for
      cfg    <- ZIO.service[AppConfig]
      client <- ZIO.service[DynamoDbAsyncClient]
      scanamo = ScanamoAsync(client)
      table   = scanamo.Table[AuditRecord](cfg.dynamo.tableName)
      result <- f(table)
    yield result
