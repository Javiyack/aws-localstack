package com.pipeline.domain

import zio.test.*
import zio.test.Assertion.*
import java.time.Instant

object PerformanceIntervalSpec extends ZIOSpecDefault:

  private val base = PerformanceInterval(
    dispatchUnit = "DU-A",
    dttmUtc      = Instant.parse("2026-03-20T10:00:00Z"),
    minValue     = 100.0,
    maxValue     = 200.0,
    sumValue     = 300.0,
    count        = 2
  )

  def spec = suite("PerformanceInterval")(

    suite("merge")(
      test("acumula correctamente dos intervalos") {
        val incoming = base.copy(minValue = 50.0, maxValue = 250.0, sumValue = 150.0, count = 1)
        val merged   = base.merge(incoming)
        assertTrue(
          merged.minValue == 50.0,
          merged.maxValue == 250.0,
          merged.sumValue == 450.0,
          merged.count    == 3
        )
      },
      test("merge con sí mismo duplica la suma y el count") {
        val merged = base.merge(base)
        assertTrue(
          merged.minValue == base.minValue,
          merged.maxValue == base.maxValue,
          merged.sumValue == 600.0,
          merged.count    == 4
        )
      },
      test("el resultado conserva dispatchUnit y dttmUtc del receptor") {
        val other = base.copy(dispatchUnit = "DU-B")
        val merged = base.merge(other)
        assertTrue(merged.dispatchUnit == "DU-A")
      }
    )
  )
