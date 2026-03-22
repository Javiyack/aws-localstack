package com.pipeline.domain

import zio.test.*
import zio.test.Assertion.*
import java.time.Instant

object PerformanceIntervalSpec extends ZIOSpecDefault:

  private val now = Instant.parse("2026-03-20T10:00:00Z")

  private val base = PerformanceInterval(
    dispatchUnit  = "DU-A",
    nodeId        = "node-1",
    dttmUtc       = now,
    meteredValue  = Some(100.0),
    baselineValue = None
  )

  def spec = suite("PerformanceInterval")(

    suite("merge")(
      test("merge combina meteredValue y baselineValue en un solo intervalo") {
        val withBaseline = base.copy(meteredValue = None, baselineValue = Some(200.0))
        val merged       = base.merge(withBaseline)
        assertTrue(
          merged.meteredValue  == Some(100.0),
          merged.baselineValue == Some(200.0)
        )
      },
      test("merge — el valor más reciente sobreescribe el anterior") {
        val updated = base.copy(meteredValue = Some(150.0))
        val merged  = base.merge(updated)
        assertTrue(merged.meteredValue == Some(150.0))
      },
      test("el resultado conserva dispatchUnit y dttmUtc del receptor") {
        val other  = base.copy(nodeId = "node-2", dispatchUnit = "DU-B")
        val merged = base.merge(other)
        assertTrue(merged.dispatchUnit == "DU-A", merged.nodeId == "node-1")
      },
      test("merge preserva meteredValue antiguo cuando el nuevo es None") {
        val noMetered = base.copy(meteredValue = None, baselineValue = Some(999.0))
        val merged    = base.merge(noMetered)
        assertTrue(
          merged.meteredValue  == Some(100.0),
          merged.baselineValue == Some(999.0)
        )
      }
    )
  )
