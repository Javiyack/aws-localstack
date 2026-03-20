package com.pipeline.layers

import com.pipeline.config.AppConfig
import com.pipeline.application.PipelineProcessor
import slick.jdbc.PostgresProfile.api.Database
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import sttp.client3.SttpBackend
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.redis.*

import java.net.URI

/** Cableado completo de layers ZIO para producción y LocalStack.
 *
 *  El flag `localstack` en [[AppConfig]] conmuta el endpoint de cada
 *  cliente AWS hacia `http://localhost:4566`.
 */
object AppLayers:

  val live: ZLayer[Any, Throwable, PipelineProcessor.Env] =
    ZLayer.makeSome[Any, PipelineProcessor.Env](
      AppConfig.layer,
      kinesisLayer,
      dynamoLayer,
      redisLayer,
      postgresLayer,
      sttpLayer
    )

  // ─── Kinesis ─────────────────────────────────────────────────────────────

  private val kinesisLayer: ZLayer[AppConfig, Throwable, KinesisAsyncClient] =
    ZLayer.scoped {
      ZIO.serviceWithZIO[AppConfig] { cfg =>
        ZIO.acquireRelease(
          ZIO.attempt {
            val builder = KinesisAsyncClient.builder().region(Region.of(cfg.kinesis.region))
            if cfg.localstack then
              builder
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(
                  StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
                )
                .build()
            else
              builder.build()
          }
        )(client => ZIO.succeed(client.close()))
      }
    }

  // ─── DynamoDB ─────────────────────────────────────────────────────────────

  private val dynamoLayer: ZLayer[AppConfig, Throwable, DynamoDbAsyncClient] =
    ZLayer.scoped {
      ZIO.serviceWithZIO[AppConfig] { cfg =>
        ZIO.acquireRelease(
          ZIO.attempt {
            val builder = DynamoDbAsyncClient.builder().region(Region.of(cfg.dynamo.region))
            if cfg.localstack then
              builder
                .endpointOverride(URI.create("http://localhost:4566"))
                .credentialsProvider(
                  StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"))
                )
                .build()
            else
              builder.build()
          }
        )(client => ZIO.succeed(client.close()))
      }
    }

  // ─── Redis ─────────────────────────────────────────────────────────────────

  private val redisLayer: ZLayer[AppConfig, Throwable, Redis] = {
    val configLayer: ZLayer[AppConfig, Nothing, RedisConfig] =
      ZLayer.fromZIO(
        ZIO.serviceWith[AppConfig](cfg => RedisConfig(cfg.redis.host, cfg.redis.port))
      )
    val codecLayer: ZLayer[Any, Nothing, CodecSupplier] =
      ZLayer.succeed(CodecSupplier.utf8)
    (configLayer ++ codecLayer) >>> Redis.singleNode
  }

  // ─── PostgreSQL (Slick) ────────────────────────────────────────────────────

  private val postgresLayer: ZLayer[AppConfig, Throwable, Database] =
    ZLayer.scoped {
      ZIO.serviceWithZIO[AppConfig] { cfg =>
        ZIO.acquireRelease(
          ZIO.attempt {
            Database.forURL(
              url    = s"jdbc:postgresql://${cfg.postgres.host}:${cfg.postgres.port}/${cfg.postgres.database}",
              user   = cfg.postgres.user,
              password = cfg.postgres.password,
              driver = "org.postgresql.Driver"
            )
          }
        )(db => ZIO.succeed(db.close()))
      }
    }

  // ─── sttp HTTP backend ─────────────────────────────────────────────────────

  private val sttpLayer: ZLayer[Any, Throwable, SttpBackend[Task, Any]] =
    ZLayer.scoped(HttpClientZioBackend.scoped())
