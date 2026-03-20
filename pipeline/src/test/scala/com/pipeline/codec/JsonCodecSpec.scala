package com.pipeline.codec

import com.pipeline.domain.*
import io.circe.parser.*
import io.circe.syntax.*
import zio.test.*
import zio.test.Assertion.*
import java.time.Instant

object JsonCodecSpec extends ZIOSpecDefault:

  private val now = Instant.parse("2026-03-20T10:00:00Z")

  def spec = suite("JsonCodecs")(

    suite("InputMessage")(
      test("round-trip encode/decode") {
        val original = InputMessage("node-1", now, Some("reg-123"), None)
        val json     = original.asJson.noSpaces
        val decoded  = decode[InputMessage](json)
        assertTrue(decoded == Right(original))
      },
      test("decode falla con JSON inválido") {
        val result = decode[InputMessage]("""{"not":"valid"}""")
        assertTrue(result.isLeft)
      }
    ),

    suite("PerformanceInterval")(
      test("round-trip encode/decode") {
        val original = PerformanceInterval("DU-A", now, 100.0, 200.0, 300.0, 2)
        val json     = original.asJson.noSpaces
        val decoded  = decode[PerformanceInterval](json)
        assertTrue(decoded == Right(original))
      }
    ),

    suite("AuditRecord")(
      test("round-trip encode/decode") {
        val msg      = InputMessage("node-1", now, Some("reg-123"), None)
        val original = AuditRecord.from(msg, now)
        val json     = original.asJson.noSpaces
        val decoded  = decode[AuditRecord](json)
        assertTrue(decoded == Right(original))
      },
      test("el TTL es 24 horas posterior a dttmUtc") {
        val msg    = InputMessage("node-1", now, Some("r"), None)
        val record = AuditRecord.from(msg, now)
        assertTrue(record.ttl == now.plusSeconds(86400).getEpochSecond)
      }
    ),

    suite("Instant codec")(
      test("serializa a ISO-8601 string") {
        val json = now.asJson.noSpaces
        assertTrue(json == s""""${now.toString}"""")
      },
      test("deserializa desde ISO-8601 string") {
        val result = decode[Instant](s""""${now.toString}"""")
        assertTrue(result == Right(now))
      }
    )
  )
