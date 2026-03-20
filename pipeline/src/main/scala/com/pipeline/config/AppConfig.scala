package com.pipeline.config

import zio.*

final case class KinesisConfig(
  endpoint:          String,
  region:            String,
  inputStreamName:   String,
  outputStreamName:  String
)

final case class DynamoConfig(
  endpoint:  String,
  region:    String,
  tableName: String
)

final case class RedisConfig(
  host: String,
  port: Int
)

final case class PostgresConfig(
  host:     String,
  port:     Int,
  database: String,
  user:     String,
  password: String
)

final case class ValueBackendConfig(
  baseUrl: String
)

final case class AppConfig(
  kinesis:      KinesisConfig,
  dynamo:       DynamoConfig,
  redis:        RedisConfig,
  postgres:     PostgresConfig,
  valueBackend: ValueBackendConfig,
  localstack:   Boolean = false
)

object AppConfig:

  val layer: ZLayer[Any, Throwable, AppConfig] =
    ZLayer.fromZIO(ZIO.attempt {
      AppConfig(
        kinesis = KinesisConfig(
          endpoint         = sys.env.getOrElse("KINESIS_ENDPOINT",          "http://localhost:4566"),
          region           = sys.env.getOrElse("KINESIS_REGION",            "us-east-1"),
          inputStreamName  = sys.env.getOrElse("KINESIS_INPUT_STREAM_NAME",  "input-stream"),
          outputStreamName = sys.env.getOrElse("KINESIS_OUTPUT_STREAM_NAME", "output-stream")
        ),
        dynamo = DynamoConfig(
          endpoint  = sys.env.getOrElse("DYNAMO_ENDPOINT",  "http://localhost:4566"),
          region    = sys.env.getOrElse("DYNAMO_REGION",    "us-east-1"),
          tableName = sys.env.getOrElse("DYNAMO_TABLE_NAME","audit-records")
        ),
        redis = RedisConfig(
          host = sys.env.getOrElse("REDIS_HOST", "localhost"),
          port = sys.env.getOrElse("REDIS_PORT", "6379").toInt
        ),
        postgres = PostgresConfig(
          host     = sys.env.getOrElse("POSTGRES_HOST",     "localhost"),
          port     = sys.env.getOrElse("POSTGRES_PORT",     "5432").toInt,
          database = sys.env.getOrElse("POSTGRES_DATABASE", "pipeline"),
          user     = sys.env.getOrElse("POSTGRES_USER",     "pipeline"),
          password = sys.env.getOrElse("POSTGRES_PASSWORD", "pipeline")
        ),
        valueBackend = ValueBackendConfig(
          baseUrl = sys.env.getOrElse("VALUE_BACKEND_BASE_URL", "http://localhost:3333")
        ),
        localstack = sys.env.getOrElse("LOCALSTACK", "true").toBoolean
      )
    })
