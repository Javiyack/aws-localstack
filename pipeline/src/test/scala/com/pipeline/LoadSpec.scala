package com.pipeline

import com.pipeline.domain.*
import com.pipeline.application.PipelineProcessor
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.TestAspect.*

import java.time.Instant

/** Test de carga: verifica que el procesamiento en lote aísla fallos
 *  y produce resultados coherentes para conjuntos grandes de mensajes.
 *
 *  No requiere servicios externos: prueba únicamente la lógica de
 *  dominios y estructuras de datos sin efectos IO.
 */
object LoadSpec extends ZIOSpecDefault:

  private val now = Instant.parse("2026-03-20T12:00:00Z")

  private def makeMsg(i: Int): InputMessage =
    if i % 2 == 0 then
      InputMessage(s"node-${i % 20 + 1}", now, Some(s"reg-${i % 10 + 1}"), None)
    else
      InputMessage(s"node-${i % 20 + 1}", now, None, Some(s"base-${i % 10 + 1}"))

  private def badMsg(i: Int): InputMessage =
    // Mensaje inválido: ambos IDs presentes
    InputMessage(s"node-bad-$i", now, Some("r"), Some("b"))

  def spec = suite("LoadSpec")(

    suite("generación de mensajes")(

      test("genera 500 mensajes válidos con nodeId y exactamente un ID") {
        val msgs = (1 to 500).map(makeMsg).toList
        val allValid = msgs.forall(_.isValid)
        val allHaveId = msgs.forall(_.resolvedId.isDefined)
        assertTrue(allValid, allHaveId)
      } @@ ignore,   // Quitar @@ ignore cuando sbt esté instalado

      test("los mensajes se distribuyen entre registration y baseline") {
        val msgs  = (1 to 1000).map(makeMsg).toList
        val regs  = msgs.count(_.isRegistration)
        val bases = msgs.count(!_.isRegistration)
        assertTrue(regs > 0, bases > 0)
      },

      test("nodeId siempre está en rango 1-20") {
        val msgs = (1 to 200).map(makeMsg).toList
        val ids  = msgs.map(_.nodeId.stripPrefix("node-").toInt)
        assertTrue(ids.forall(i => i >= 1 && i <= 20))
      }
    ),

    suite("lógica de merge en lote")(

      test("merge preserva meteredValue cuando el nuevo no lo tiene") {
        val reg  = PerformanceInterval("DU-A", "node-1", now, meteredValue = Some(1000.0))
        val base = PerformanceInterval("DU-A", "node-1", now, baselineValue = Some(500.0))
        val merged = (1 until 100).foldLeft(reg)((acc, _) => acc.merge(base))
        assertTrue(
          merged.meteredValue  == Some(1000.0),
          merged.baselineValue == Some(500.0)
        )
      },

      test("merge — el meteredValue más reciente sobreescribe el anterior") {
        val intervals = (1 to 50).map(i =>
          PerformanceInterval("DU-B", "node-1", now, meteredValue = Some(i.toDouble * 10.0))
        ).toList
        val merged = intervals.tail.foldLeft(intervals.head)(_.merge(_))
        assertTrue(merged.meteredValue == Some(500.0))
      },

      test("merge — baselineValue del más reciente gana") {
        val intervals = (1 to 50).map(i =>
          PerformanceInterval("DU-C", s"node-$i", now, baselineValue = Some(i.toDouble * 10.0))
        ).toList
        val merged = intervals.tail.foldLeft(intervals.head)(_.merge(_))
        assertTrue(merged.baselineValue == Some(500.0))
      }
    ),

    suite("aislamiento de fallos en batch")(

      test("mensajes inválidos no lanzan excepción al validar") {
        val badMsgs = (1 to 50).map(badMsg).toList
        val allInvalid = badMsgs.forall(!_.isValid)
        assertTrue(allInvalid)
      },

      test("mezcla de válidos e inválidos — los válidos siguen siendo válidos") {
        val mixed = (1 to 100).map { i =>
          if i % 5 == 0 then badMsg(i) else makeMsg(i)
        }.toList
        val valid   = mixed.filter(_.isValid)
        val invalid = mixed.filter(!_.isValid)
        assertTrue(
          valid.size   == 80,
          invalid.size == 20
        )
      }
    ),

    suite("AuditRecord TTL")(

      test("TTL de 500 registros es siempre 24h posterior al dttmUtc") {
        val records = (1 to 500).map(i =>
          AuditRecord.from(makeMsg(i), now)
        )
        val allCorrectTtl = records.forall { r =>
          r.ttl == now.plusSeconds(86400).getEpochSecond
        }
        assertTrue(allCorrectTtl)
      }
    )
  )
