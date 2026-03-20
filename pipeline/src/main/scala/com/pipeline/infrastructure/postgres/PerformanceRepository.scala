package com.pipeline.infrastructure.postgres

import com.pipeline.config.AppConfig
import com.pipeline.domain.PerformanceInterval
import slick.jdbc.PostgresProfile.api.*
import zio.*
import zio.interop.reactivestreams.*

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

  // Row tuple: (dispatchUnit, dttmUtc, minValue, maxValue, sumValue, count)
  private type Row = (String, Timestamp, Double, Double, Double, Int)

  private class PerformanceTable(tag: Tag)
      extends Table[Row](tag, "performance_intervals"):

    def dispatchUnit = column[String]("dispatch_unit")
    def dttmUtc      = column[Timestamp]("dttm_utc")
    def minValue     = column[Double]("min_value")
    def maxValue     = column[Double]("max_value")
    def sumValue     = column[Double]("sum_value")
    def count        = column[Int]("count")

    def pk = primaryKey("pk_performance_intervals", (dispatchUnit, dttmUtc))

    def * = (dispatchUnit, dttmUtc, minValue, maxValue, sumValue, count)

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
        (dispatch_unit, dttm_utc, min_value, max_value, sum_value, count)
      VALUES
        (${i.dispatchUnit}, ${Timestamp.from(i.dttmUtc)},
         ${i.minValue}, ${i.maxValue}, ${i.sumValue}, ${i.count})
      ON CONFLICT (dispatch_unit, dttm_utc) DO UPDATE SET
        min_value = LEAST(EXCLUDED.min_value,     performance_intervals.min_value),
        max_value = GREATEST(EXCLUDED.max_value,  performance_intervals.max_value),
        sum_value = performance_intervals.sum_value + EXCLUDED.sum_value,
        count     = performance_intervals.count   + EXCLUDED.count
    """

  private def run[R](action: DBIO[R]): ZIO[Env, Throwable, R] =
    ZIO.serviceWithZIO[Database] { db =>
      ZIO.fromFuture(_ => db.run(action))
    }

  private def rowToInterval(row: Row): PerformanceInterval =
    PerformanceInterval(
      dispatchUnit = row._1,
      dttmUtc      = row._2.toInstant,
      minValue     = row._3,
      maxValue     = row._4,
      sumValue     = row._5,
      count        = row._6
    )
