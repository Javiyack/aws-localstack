package com.pipeline.infrastructure.postgres

import com.pipeline.config.AppConfig
import com.pipeline.domain.PerformanceInterval
import slick.jdbc.PostgresProfile.api.{Tag as SlickTag, *}
import zio.*

import java.time.Instant
import java.sql.Timestamp

/** Repositorio PostgreSQL para `performance_intervals`.
 *
 *  Usa Slick con el perfil PostgreSQL.
 *  Cada `upsert` realiza un INSERT … ON CONFLICT DO UPDATE (idempotente).
 *  La tabla se creó con la migración Flyway V1.
 */
object PerformanceRepository:

  type Env = Database & AppConfig

  // ─── Table mapping ────────────────────────────────────────────────────────

  private type Row = (String, String, Timestamp, Option[Double], Option[Double], Option[String])

  private class PerformanceTable(tag: SlickTag)
      extends Table[Row](tag, "performance_intervals"):

    def dispatchUnit  = column[String]("dispatch_unit")
    def nodeId        = column[String]("node_id")
    def dttmUtc       = column[Timestamp]("dttm_utc")
    def meteredValue  = column[Option[Double]]("metered_value")
    def baselineValue = column[Option[Double]]("baseline_value")
    def baselineId    = column[Option[String]]("baseline_id")

    def pk = primaryKey("pk_performance_intervals", (dispatchUnit, dttmUtc))

    def * = (dispatchUnit, nodeId, dttmUtc, meteredValue, baselineValue, baselineId)

  private val table = TableQuery[PerformanceTable]

  // ─── Public API ───────────────────────────────────────────────────────────

  /** INSERT OR UPDATE del intervalo en PostgreSQL. */
  def upsert(interval: PerformanceInterval): ZIO[Env, Throwable, Unit] =
    run(upsertAction(interval)).unit

  /** Upsert en paralelo de una lista de intervalos. */
  def upsertAll(intervals: List[PerformanceInterval]): ZIO[Env, Throwable, Unit] =
    ZIO.foreachPar(intervals)(upsert).unit

  /** Consulta intervalos de una unidad en un rango de tiempo. */
  def query(
    dispatchUnit: String,
    from: Instant,
    to: Instant
  ): ZIO[Env, Throwable, List[PerformanceInterval]] =
    run(
      table
        .filter(r =>
          r.dispatchUnit === dispatchUnit &&
          r.dttmUtc >= Timestamp.from(from) &&
          r.dttmUtc <= Timestamp.from(to)
        )
        .result
    ).map(_.map(rowToInterval).toList)

  // ─── private ──────────────────────────────────────────────────────────────

  private def upsertAction(i: PerformanceInterval): DBIO[Int] =
    sqlu"""
      INSERT INTO performance_intervals
        (dispatch_unit, node_id, dttm_utc, metered_value, baseline_value, baseline_id)
      VALUES
        (${i.dispatchUnit}, ${i.nodeId}, ${Timestamp.from(i.dttmUtc)},
         ${i.meteredValue}, ${i.baselineValue})
      ON CONFLICT (dispatch_unit, dttm_utc) DO UPDATE SET
        metered_value  = COALESCE(EXCLUDED.metered_value,  performance_intervals.metered_value),
        baseline_value = COALESCE(EXCLUDED.baseline_value, performance_intervals.baseline_value),
        baseline_id    = COALESCE(EXCLUDED.baseline_id,    performance_intervals.baseline_id),
        updated_at     = NOW()
    """

  private def run[R](action: DBIO[R]): ZIO[Env, Throwable, R] =
    ZIO.serviceWithZIO[Database] { db =>
      ZIO.fromFuture(_ => db.run(action))
    }

  private def rowToInterval(row: Row): PerformanceInterval =
    PerformanceInterval(
      dispatchUnit  = row._1,
      nodeId        = row._2,
      dttmUtc       = row._3.toInstant,
      meteredValue  = row._4,
      baselineValue = row._5
    )
