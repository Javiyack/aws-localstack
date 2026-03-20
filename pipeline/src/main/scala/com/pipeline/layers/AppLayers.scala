package com.pipeline.layers

import com.pipeline.config.AppConfig
import com.pipeline.application.PipelineProcessor
import slick.jdbc.PostgresProfile.api.Database
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio.*
import zio.redis.*
import zio.redis.embedded.EmbeddedRedis

import java.net.URI
import java.util.Properties

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
            val builder = DynamoDbAsyncClient.builder().region(Region.of(cfg.kinesis.region))
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

  private val redisLayer: ZLayer[AppConfig, Throwable, Redis] =
    ZLayer.makeSome[AppConfig, Redis](
      ZLayer.fromZIO(
        ZIO.serviceWith[AppConfig] { cfg =>
          RedisConfig(cfg.redis.host, cfg.redis.port)
        }
      ),
      RedisExecutor.layer,
      Redis.layer
    )

  // ─── PostgreSQL (Slick) ────────────────────────────────────────────────────

  private val postgresLayer: ZLayer[AppConfig, Throwable, Database] =
    ZLayer.scoped {
      ZIO.serviceWithZIO[AppConfig] { cfg =>
        ZIO.acquireRelease(
          ZIO.attempt {
            val props = new Properties()
            props.setProperty("url",      cfg.postgres.url)
            props.setProperty("user",     cfg.postgres.user)
            props.setProperty("password", cfg.postgres.password)
            props.setProperty("driver",  "org.postgresql.Driver")
            Database.forConfig("", props)
          }
        )(db => ZIO.succeed(db.close()))
      }
    }

  // ─── sttp HTTP backend ─────────────────────────────────────────────────────

  private val sttpLayer: ZLayer[Any, Throwable, SttpBackend[Task, Any]] =
    ZLayer.scoped(AsyncHttpClientZioBackend.scoped())
