# Pipeline — Scala 3 / ZIO / AWS Lambda

Módulo de procesamiento de eventos Kinesis. Se despliega como función AWS Lambda.

## Stack

| Tecnología | Versión |
|---|---|
| Scala | 3.5.2 |
| ZIO | 2.1.13 |
| ZIO AWS | 7.28.16.2 |
| sbt | 1.10.7 |
| Java runtime | 21 |

## Variables de entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `KINESIS_INPUT_STREAM_NAME` | Stream Kinesis de entrada | `input-stream` |
| `KINESIS_OUTPUT_STREAM_NAME` | Stream Kinesis de salida | `output-stream` |
| `KINESIS_REGION` | Región AWS | `us-east-1` |
| `DYNAMO_TABLE_NAME` | Tabla DynamoDB de auditoría | `audit-records` |
| `REDIS_HOST` | Host Redis | `localhost` |
| `REDIS_PORT` | Puerto Redis | `6379` |
| `POSTGRES_HOST` | Host PostgreSQL | `localhost` |
| `POSTGRES_PORT` | Puerto PostgreSQL | `5432` |
| `POSTGRES_DATABASE` | Base de datos | `pipeline` |
| `POSTGRES_USER` | Usuario | `pipeline` |
| `POSTGRES_PASSWORD` | Contraseña | *(secrets manager)* |
| `VALUE_BACKEND_BASE_URL` | URL del value-backend | `http://localhost:3000` |
| `LOCALSTACK` | `true` en local, `false` en AWS | `true` |

## Empaquetado

```bash
# Genera pipeline/target/scala-3.5.2/pipeline-assembly.jar
sbt assembly
```

## Tests

```bash
sbt test

# Con cobertura (umbral 80%)
sbt coverage test coverageReport
# Reporte en: target/scala-3.5.2/scoverage-report/index.html
```

## Formato de código

```bash
sbt scalafmt          # formatear
sbt scalafmtCheck     # verificar sin modificar
```

## Arquitectura de procesamiento

```
Kinesis input-stream
        ↓
  LambdaHandler (entry point)
        ↓
  PipelineProcessor (7 pasos)
   1. Validar InputMessage
   2. Resolver ID (registration vs baseline)
   3. Obtener valor → value-backend
   4. Construir PerformanceInterval
   5. Upsert Redis (cache caliente)
   6. Upsert PostgreSQL (histórico)
   7. Auditoría DynamoDB
        ↓
  Kinesis output-stream (fan-out)
```

Mensajes fallidos van a la **SQS DLQ** tras 3 reintentos.

winget install EclipseAdoptium.Temurin.21.JDK
winget install Docker.DockerDesktop
winget install Hashicorp.Terraform
winget install Coursier.Coursier
cs install sbt
winget install Amazon.AWSCLI
