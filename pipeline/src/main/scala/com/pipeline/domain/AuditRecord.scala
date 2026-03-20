package com.pipeline.domain

import java.time.Instant

/** Registro de auditoría persistido en DynamoDB por cada mensaje procesado.
 *  TTL = dttm_utc + 24 horas (en epoch seconds).
 */
final case class AuditRecord(
  nodeId:         String,
  dttmUtc:        String,          // ISO-8601 String (clave de rango en DynamoDB)
  registrationId: Option[String],
  baselineId:     Option[String],
  receivedAt:     String,          // ISO-8601 — momento de recepción
  ttl:            Long             // epoch seconds = dttm_utc + 24h
)

object AuditRecord:
  def from(msg: InputMessage, now: Instant): AuditRecord =
    AuditRecord(
      nodeId         = msg.nodeId,
      dttmUtc        = msg.dttmUtc.toString,
      registrationId = msg.registrationId,
      baselineId     = msg.baselineId,
      receivedAt     = now.toString,
      ttl            = msg.dttmUtc.plusSeconds(86400L).getEpochSecond
    )
