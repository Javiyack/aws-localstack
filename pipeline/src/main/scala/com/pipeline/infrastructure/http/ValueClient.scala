package com.pipeline.infrastructure.http

import com.pipeline.config.AppConfig
import sttp.client3.*
import zio.*

/** Cliente HTTP para obtener valores del servicio value-backend.
 *
 *  GET /<id> → 200 { "id": "…", "value": 987.6543 }
 *  Reintenta hasta 3 veces con backoff exponencial.
 *  Timeout por request: 5 segundos.
 */
object ValueClient:

  type Env = SttpBackend[Task, Any] & AppConfig

  final case class ValueResponse(id: String, value: Double)

  /** Obtiene el valor para el identificador dado.
   *
   *  @param id  registrationId o baselineId del mensaje
   *  @return    valor decimal (4 cifras)
   */
  def getValue(id: String): ZIO[Env, Throwable, Double] =
    ZIO.serviceWithZIO[AppConfig] { cfg =>
      val url = uri"${cfg.valueBackend.baseUrl}/$id"
      val request = basicRequest
        .get(url)
        .response(asString.mapRight(parseValue))

      sendWithRetry(request)
        .flatMap(resp =>
          resp.body match
            case Right(Right(v)) => ZIO.succeed(v)
            case Right(Left(e))  => ZIO.fail(new RuntimeException(s"Parse error: $e"))
            case Left(err)       => ZIO.fail(new RuntimeException(s"HTTP error: $err"))
        )
    }

  // ─── private ──────────────────────────────────────────────────────────────

  /** Reintentos con backoff exponencial solo para errores de red recuperables.
   *  - ConnectException / SocketTimeoutException → reintentar
   *  - 4xx del servidor → no reintentar (error permanente)
   *  - 5xx del servidor → reintentar
   */
  private val retrySchedule: Schedule[Any, Throwable, Any] =
    Schedule.exponential(200.millis) &&
    Schedule.recurs(3) &&
    Schedule.recurWhile[Throwable] {
      case _: java.net.ConnectException       => true
      case _: java.net.SocketTimeoutException => true
      case _: java.net.SocketException        => true
      case e: RuntimeException
        if e.getMessage != null &&
           e.getMessage.startsWith("HTTP error: 5") => true
      case _                                  => false
    }

  private def sendWithRetry[T](
    request: Request[T, Any]
  ): ZIO[SttpBackend[Task, Any], Throwable, Response[T]] =
    ZIO.serviceWithZIO[SttpBackend[Task, Any]] { backend =>
      request
        .send(backend)
        .retry(retrySchedule)
    }

  private def parseValue(body: String): Either[String, Double] =
    import io.circe.parser.*
    parse(body)
      .flatMap(_.hcursor.get[Double]("value"))
      .left.map(_.getMessage)
