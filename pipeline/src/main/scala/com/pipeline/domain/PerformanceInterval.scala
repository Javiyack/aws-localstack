package com.pipeline.domain

import java.time.Instant

/** Resultado del procesamiento de un mensaje, publicado al stream de salida
 *  y persistido en Redis y PostgreSQL.
 *
 *  Reglas de población:
 *  - Si el mensaje de entrada tenía registrationId: meteredValue ← respuesta HTTP
 *  - Si el mensaje de entrada tenía baselineId: baselineValue ← respuesta HTTP
 */
final case class PerformanceInterval(
  dispatchUnit:  String,
  nodeId:        String,
  dttmUtc:       Instant,
  meteredValue:  Option[Double] = None,
  baselineValue: Option[Double] = None,
  baselineId:    Option[String] = None
):
  /** Combina este objeto con uno más reciente, conservando campos previos
   *  cuando el nuevo los deja indefinidos.
   *
   *  Ejemplo:
   *  {{{ prev.merge(next) }}}
   *  Si prev tiene meteredValue y next tiene baselineValue,
   *  el resultado tendrá ambos.
   */
  def merge(newer: PerformanceInterval): PerformanceInterval =
    copy(
      meteredValue  = newer.meteredValue.orElse(meteredValue),
      baselineValue = newer.baselineValue.orElse(baselineValue),
      baselineId    = newer.baselineId.orElse(baselineId)
    )
