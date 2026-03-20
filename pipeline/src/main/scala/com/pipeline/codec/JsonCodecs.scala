package com.pipeline.codec

import com.pipeline.domain.*
import io.circe.*
import io.circe.generic.semiauto.*
import java.time.Instant

object JsonCodecs:

  // ── Instant ───────────────────────────────────────────────────────────────
  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] = Decoder.decodeString.emapTry(s =>
    scala.util.Try(Instant.parse(s))
  )

  // ── Dominio ───────────────────────────────────────────────────────────────
  given Encoder[InputMessage]        = deriveEncoder
  given Decoder[InputMessage]        = deriveDecoder
  given Encoder[PerformanceInterval] = deriveEncoder
  given Decoder[PerformanceInterval] = deriveDecoder
  given Encoder[AuditRecord]         = deriveEncoder
  given Decoder[AuditRecord]         = deriveDecoder
