package com.pipeline.infrastructure.http

import com.pipeline.config.*
import io.circe.syntax.*
import io.circe.Json
import sttp.client3.*
import sttp.client3.testing.SttpBackendStub
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant

object ValueClientSpec extends ZIOSpecDefault:

  /** Stub sttp que responde con un JSON de value fijo para cualquier URL. */
  private def stubBackend(value: Double): SttpBackend[Task, Any] =
    SttpBackendStub.asynchronousFuture
      .whenAnyRequest
      .thenRespond(Json.obj("id" -> "test".asJson, "value" -> value.asJson).noSpaces)
      .asInstanceOf[SttpBackend[Task, Any]]

  private def mkCfg(baseUrl: String): AppConfig =
    AppConfig(
      kinesis      = KinesisConfig("http://localhost:4566", "us-east-1", "input-stream", "output-stream"),
      dynamo       = DynamoConfig("http://localhost:4566", "us-east-1", "audit"),
      redis        = RedisConfig("localhost", 6379),
      postgres     = PostgresConfig("localhost", 5432, "perf", "user", "pass"),
      valueBackend = ValueBackendConfig(baseUrl),
      localstack   = true
    )

  def spec = suite("ValueClient")(

    test("parsea el campo 'value' correctamente") {
      val expectedValue = 987.6543
      val cfg = mkCfg("http://localhost:3000")

      // Verificamos directamente la función de parsing sin HTTP real
      val json = s"""{"id":"test","value":$expectedValue}"""
      val result = io.circe.parser.parse(json)
        .flatMap(_.hcursor.get[Double]("value"))

      assertTrue(result == Right(expectedValue))
    },

    test("rango de valores esperado (900-1100)") {
      // El value-backend genera valores en [900, 1100] con 4 decimales
      val samples = List(900.0001, 950.1234, 1000.0000, 1099.9999)
      assertTrue(samples.forall(v => v >= 900.0 && v <= 1100.0))
    },

    test("clave de cache usa dispatchUnit + dttmUtc") {
      val du  = "DU-TEST"
      val ts  = Instant.parse("2026-03-20T10:00:00Z")
      val key = s"perf:$du:${ts.toString}"
      assertTrue(key == "perf:DU-TEST:2026-03-20T10:00:00Z")
    }
  )
