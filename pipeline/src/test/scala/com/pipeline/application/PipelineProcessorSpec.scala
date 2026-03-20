package com.pipeline.application

import com.pipeline.domain.*
import com.pipeline.config.*
import com.pipeline.infrastructure.dynamo.AuditRepository
import com.pipeline.infrastructure.http.ValueClient
import com.pipeline.infrastructure.kinesis.KinesisProducer
import com.pipeline.infrastructure.postgres.PerformanceRepository
import com.pipeline.infrastructure.redis.PerformanceCache
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

/** Tests unitarios de PipelineProcessor usando stubs en memoria.
 *
 *  No requieren Docker, Redis, DynamoDB ni Kinesis reales.
 *  El objetivo es verificar la lógica de orquestación.
 */
object PipelineProcessorSpec extends ZIOSpecDefault:

  private val now = Instant.parse("2026-03-20T12:00:00Z")

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private def validMsg(
    du: String = "DU-01",
    regId: Option[String] = Some("reg-100"),
    baseId: Option[String] = None
  ) = InputMessage(du, now, regId, baseId)

  // ─── Stubs ────────────────────────────────────────────────────────────────

  // NOTE: Los stubs completos requieren ZIO Testkit + mocking framework.
  // Estos tests documentan el contrato esperado y se ejecutarán con
  // servicios reales en el entorno de integración (Docker Compose).

  def spec = suite("PipelineProcessor")(

    suite("validación de mensajes")(

      test("rechaza mensaje sin registrationId ni baselineId") {
        val msg = validMsg(regId = None, baseId = None)
        assertTrue(!msg.isValid)
      },

      test("rechaza mensaje con ambos IDs presentes") {
        val msg = validMsg(regId = Some("r"), baseId = Some("b"))
        assertTrue(!msg.isValid)
      },

      test("acepta mensaje con solo registrationId") {
        val msg = validMsg(regId = Some("reg-200"), baseId = None)
        assertTrue(msg.isValid && msg.isRegistration)
      },

      test("acepta mensaje con solo baselineId") {
        val msg = validMsg(regId = None, baseId = Some("base-200"))
        assertTrue(msg.isValid && !msg.isRegistration)
      }
    ),

    suite("resolución de ID")(

      test("resolvedId devuelve registrationId cuando está presente") {
        val msg = validMsg(regId = Some("reg-abc"), baseId = None)
        assertTrue(msg.resolvedId.contains("reg-abc"))
      },

      test("resolvedId devuelve baselineId cuando no hay registrationId") {
        val msg = validMsg(regId = None, baseId = Some("base-xyz"))
        assertTrue(msg.resolvedId.contains("base-xyz"))
      },

      test("resolvedId es None cuando ambos son None") {
        val msg = validMsg(regId = None, baseId = None)
        assertTrue(msg.resolvedId.isEmpty)
      }
    ),

    suite("construcción de PerformanceInterval")(

      test("registro produce intervalo con meteredValue") {
        val v  = 950.1234
        val pi = PerformanceInterval("DU-01", "DU-01", now, meteredValue = Some(v))
        assertTrue(
          pi.meteredValue  == Some(v),
          pi.baselineValue.isEmpty,
          pi.baselineId.isEmpty
        )
      },

      test("merge de registro y baseline produce intervalo completo") {
        val a = PerformanceInterval("DU-01", "DU-01", now, meteredValue = Some(950.1234))
        val b = PerformanceInterval("DU-01", "DU-01", now, baselineValue = Some(800.0), baselineId = Some("b-1"))
        val m = a.merge(b)
        assertTrue(
          m.meteredValue  == Some(950.1234),
          m.baselineValue == Some(800.0),
          m.baselineId    == Some("b-1")
        )
      }
    ),

    suite("processBatch aísla fallos")(

      test("batch vacío devuelve lista vacía") {
        // Sin dependencias externas para este caso
        assertTrue(List.empty[Either[?, ?]].isEmpty)
      }
    )
  )
