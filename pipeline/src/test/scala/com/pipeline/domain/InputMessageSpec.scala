package com.pipeline.domain

import zio.test.*
import zio.test.Assertion.*
import java.time.Instant

object InputMessageSpec extends ZIOSpecDefault:

  private val now = Instant.parse("2026-03-20T10:00:00Z")

  def spec = suite("InputMessage")(

    suite("isValid")(
      test("es válido con solo registrationId") {
        val msg = InputMessage("n1", now, Some("reg-1"), None)
        assertTrue(msg.isValid)
      },
      test("es válido con solo baselineId") {
        val msg = InputMessage("n1", now, None, Some("base-1"))
        assertTrue(msg.isValid)
      },
      test("es inválido si ambos están presentes") {
        val msg = InputMessage("n1", now, Some("r"), Some("b"))
        assertTrue(!msg.isValid)
      },
      test("es inválido si ninguno está presente") {
        val msg = InputMessage("n1", now, None, None)
        assertTrue(!msg.isValid)
      }
    ),

    suite("resolvedId")(
      test("devuelve registrationId cuando está presente") {
        val msg = InputMessage("n1", now, Some("reg-001"), None)
        assertTrue(msg.resolvedId.contains("reg-001"))
      },
      test("devuelve baselineId cuando registrationId es None") {
        val msg = InputMessage("n1", now, None, Some("base-001"))
        assertTrue(msg.resolvedId.contains("base-001"))
      },
      test("devuelve None cuando ambos son None") {
        val msg = InputMessage("n1", now, None, None)
        assertTrue(msg.resolvedId.isEmpty)
      }
    ),

    suite("isRegistration")(
      test("true cuando tiene registrationId") {
        val msg = InputMessage("n1", now, Some("r"), None)
        assertTrue(msg.isRegistration)
      },
      test("false cuando tiene baselineId") {
        val msg = InputMessage("n1", now, None, Some("b"))
        assertTrue(!msg.isRegistration)
      }
    )
  )
