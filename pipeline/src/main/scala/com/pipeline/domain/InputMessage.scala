package com.pipeline.domain

import java.time.Instant

/** Mensaje de entrada desde el stream Kinesis.
 *
 *  Invariante: exactamente uno de registrationId o baselineId debe estar presente.
 */
final case class InputMessage(
  nodeId:         String,
  dttmUtc:        Instant,
  registrationId: Option[String],
  baselineId:     Option[String]
):
  /** Verifica que exactamente uno de los IDs opcionales esté presente. */
  def isValid: Boolean =
    (registrationId.isDefined, baselineId.isDefined) match
      case (true, false) | (false, true) => true
      case _                              => false

  /** El ID resuelto (el que esté definido). */
  def resolvedId: Option[String] =
    registrationId.orElse(baselineId)

  /** True si el mensaje tiene registrationId (vs baselineId). */
  def isRegistration: Boolean = registrationId.isDefined
  /** True si el mensaje tiene baselineId (vs registrationId). */
  def isBaseline: Boolean = baselineId.isDefined
