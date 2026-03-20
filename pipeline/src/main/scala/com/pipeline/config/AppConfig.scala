package com.pipeline.config

import zio.*
import zio.config.*
import zio.config.magnolia.*

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
  valueBackend: ValueBackendConfig
)

object AppConfig:

  private val descriptor: ConfigDescriptor[AppConfig] = deriveConfig[AppConfig]

  val layer: ULayer[AppConfig] =
    ZLayer.fromZIO(
      read(descriptor.from(ConfigSource.fromSystemEnv(keyDelimiter = Some('_'))))
        .orDie
    )
