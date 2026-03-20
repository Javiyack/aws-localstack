package com.pipeline.infrastructure.redis

import com.pipeline.config.AppConfig
import com.pipeline.domain.PerformanceInterval
import com.pipeline.codec.JsonCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import zio.*
import zio.redis.*

import java.time.Instant

/** Cache de PerformanceInterval en Redis.
 *
 *  Clave: `perf:<dispatchUnit>:<dttmUtc-ISO>`
 *  TTL:   24 horas (mismo ciclo que DynamoDB)
 *
 *  El método `upsert` aplica la lógica de acumulación incremental:
 *  si ya existe un intervalo en cache, lo fusiona con el nuevo valor
 *  usando [[PerformanceInterval.merge]].
 */
object PerformanceCache:

  type Env = Redis & AppConfig

  private val ttl = 24.hours

  /** Obtiene el intervalo almacenado para una clave dada, si existe. */
  def get(
    dispatchUnit: String,
    dttmUtc: Instant
  ): ZIO[Env, Throwable, Option[PerformanceInterval]] =
    ZIO.serviceWithZIO[Redis] { redis =>
      redis
        .get(key(dispatchUnit, dttmUtc))
        .returning[String]
        .flatMap {
          case None    => ZIO.none
          case Some(s) =>
            ZIO.fromEither(decode[PerformanceInterval](s))
              .mapError(e => new RuntimeException(s"Redis decode error: $e"))
              .map(Some(_))
        }
    }

  /** Inserta o actualiza (merge) el intervalo y refresca el TTL. */
  def upsert(
    interval: PerformanceInterval
  ): ZIO[Env, Throwable, PerformanceInterval] =
    for
      existing <- get(interval.dispatchUnit, interval.dttmUtc)
      merged    = existing.fold(interval)(_.merge(interval))
      _        <- set(merged)
    yield merged

  /** Elimina la entrada del cache. */
  def delete(
    dispatchUnit: String,
    dttmUtc: Instant
  ): ZIO[Env, Throwable, Unit] =
    ZIO.serviceWithZIO[Redis] { redis =>
      redis.del(key(dispatchUnit, dttmUtc)).unit
    }

  // ─── private ──────────────────────────────────────────────────────────────

  private def key(dispatchUnit: String, dttmUtc: Instant): String =
    s"perf:$dispatchUnit:${dttmUtc.toString}"

  private def set(interval: PerformanceInterval): ZIO[Env, Throwable, Unit] =
    ZIO.serviceWithZIO[Redis] { redis =>
      redis
        .set(
          key(interval.dispatchUnit, interval.dttmUtc),
          interval.asJson.noSpaces,
          expireTime = Some(SetExpire.Ex(ttl.toSeconds))
        )
        .unit
    }
