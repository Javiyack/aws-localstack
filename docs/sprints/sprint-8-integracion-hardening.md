# Sprint 8 — Integración Final, Hardening & Documentación

## Objetivo

Consolidar el sistema completo, añadir resiliencia y observabilidad, auditar la seguridad, y preparar el proyecto para entrega o despliegue en AWS real.

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 4 (pipeline) + Sprint 7 (dashboard)

---

## 1. Test End-to-End Completo

### Escenario de humo (smoke test)

```bash
#!/bin/bash
# scripts/e2e-smoke.sh
set -e

ENDPOINT="http://localhost:4566"

echo "== 1. Verificar servicios =="
curl -sf "$ENDPOINT/_localstack/health" | grep '"kinesis": "running"'
curl -sf http://localhost:3333/health-check || true
curl -sf http://localhost:5173 || true

echo "== 2. Publicar mensaje de prueba =="
MSG=$(cat <<EOF
{
  "nodeId":         "e2e-node-001",
  "dttmUtc":        "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "registrationId": "e2e-reg-001"
}
EOF
)
DATA=$(echo "$MSG" | base64 -w0)

AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
  aws --endpoint-url=$ENDPOINT kinesis put-record \
  --stream-name input-stream \
  --partition-key "e2e-node-001" \
  --data "$DATA"

echo "== 3. Esperar procesamiento (5s) =="
sleep 5

echo "== 4. Verificar DynamoDB =="
ITEMS=$(AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
  aws --endpoint-url=$ENDPOINT dynamodb query \
  --table-name audit-records \
  --key-condition-expression "nodeId = :n" \
  --expression-attribute-values '{":n":{"S":"e2e-node-001"}}' \
  --query 'Count')
[ "$ITEMS" -gt 0 ] && echo "✓ DynamoDB: $ITEMS registro(s)"

echo "== 5. Verificar PostgreSQL =="
psql -h localhost -U pipeline -d pipeline -c \
  "SELECT count(*) FROM performance_intervals WHERE node_id='e2e-node-001';"

echo "== 6. Verificar output-stream =="
ITERATOR=$(AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
  aws --endpoint-url=$ENDPOINT kinesis get-shard-iterator \
  --stream-name output-stream \
  --shard-id shardId-000000000000 \
  --shard-iterator-type TRIM_HORIZON \
  --query 'ShardIterator' --output text)

RECORDS=$(AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test \
  aws --endpoint-url=$ENDPOINT kinesis get-records \
  --shard-iterator "$ITERATOR" \
  --query 'Records | length(@)')
echo "✓ Output stream: $RECORDS registro(s)"

echo "== E2E OK =="
```

### Escenario de lote (batch test)

```bash
# scripts/e2e-batch.sh — enviar 500 mensajes y verificar el resultado
for i in $(seq 1 500); do
  TYPE=$((RANDOM % 2))
  if [ $TYPE -eq 0 ]; then
    ID_FIELD='"registrationId": "reg-'$(printf '%03d' $((RANDOM % 10)))'", "baselineId": null'
  else
    ID_FIELD='"baselineId": "base-'$(printf '%03d' $((RANDOM % 10)))'", "registrationId": null'
  fi
  # ... (publicar al stream)
done
```

---

## 2. Dead Letter Queue (DLQ)

Añadir en `infra/main.tf`:

```hcl
# SQS DLQ para mensajes fallidos de Lambda
resource "aws_sqs_queue" "dlq" {
  name                       = "pipeline-dlq"
  message_retention_seconds  = 1209600  # 14 días
}

# Actualizar lambda_event_source_mapping
resource "aws_lambda_event_source_mapping" "kinesis_trigger" {
  # ... propiedades existentes ...
  bisect_batch_on_function_error = true
  maximum_retry_attempts         = 3
  destination_config {
    on_failure {
      destination_arn = aws_sqs_queue.dlq.arn
    }
  }
}
```

### Monitor de DLQ en el dashboard

```typescript
// src/api/dlq.ts
export async function getDlqMessageCount(): Promise<number> {
  // GET atributo ApproximateNumberOfMessages de la queue
  // Añadir al StatusPanel como recurso adicional
}
```

---

## 3. Alarmas en CloudWatch

### `infra/alarms.tf`

```hcl
# Alarma: tasa de errores Lambda > 5%
resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "pipeline-lambda-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "La Lambda de pipeline tiene una tasa de errores elevada"

  dimensions = {
    FunctionName = aws_lambda_function.pipeline.function_name
  }
}

# Alarma: latencia Lambda > 10s
resource "aws_cloudwatch_metric_alarm" "lambda_duration" {
  alarm_name          = "pipeline-lambda-duration"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Duration"
  namespace           = "AWS/Lambda"
  period              = 60
  statistic           = "p99"
  threshold           = 10000  # ms
}
```

---

## 4. Reintentos y Circuit Breaker en ValueClient

### Actualizar `ValueClient.scala`

```scala
// Política de reintentos con backoff exponencial
val retryPolicy = Schedule.exponentialBackoff(200.millis) &&
                  Schedule.recurs(3) &&
                  Schedule.recurWhile[Throwable] {
                    case _: java.net.ConnectException    => true
                    case _: java.net.SocketTimeoutException => true
                    case _                               => false
                  }

// Circuit breaker con ZIO Resilience (o implementación manual)
// Abrir después de 5 fallos consecutivos, cerrar tras 30s
```

---

## 5. Seguridad

### 5.1 IAM — Principio de Menor Privilegio

```hcl
# infra/iam.tf
data "aws_iam_policy_document" "lambda_policy" {
  statement {
    actions   = ["kinesis:GetRecords", "kinesis:GetShardIterator", "kinesis:DescribeStream", "kinesis:ListShards"]
    resources = [aws_kinesis_stream.input.arn]
  }
  statement {
    actions   = ["kinesis:PutRecord", "kinesis:PutRecords"]
    resources = [aws_kinesis_stream.output.arn]
  }
  statement {
    actions   = ["dynamodb:PutItem"]
    resources = [aws_dynamodb_table.audit.arn]
  }
  statement {
    actions   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["arn:aws:logs:*:*:*"]
  }
}
```

### 5.2 Secrets Manager para credenciales

```hcl
resource "aws_secretsmanager_secret" "db_credentials" {
  name = "pipeline/db-credentials"
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id     = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.db_user
    password = var.db_password
    host     = var.db_host
    port     = var.db_port
    dbname   = var.db_name
  })
}
```

Actualizar `AppConfig.scala` para leer de Secrets Manager en producción:

```scala
// En producción: usar zio-aws-secretsmanager
// En local: usar variables de entorno (ya implementado)
val secretsLayer = ZLayer.fromZIO {
  ZIO.ifZIO(ZIO.succeed(sys.env.getOrElse("ENV", "local") == "production"))(
    onTrue  = loadFromSecretsManager,
    onFalse = loadFromEnv
  )
}
```

### 5.3 CORS en el Dashboard (producción)

```typescript
// vite.config.ts — producción: configurar headers CSP y CORS en el servidor
// No exponer el endpoint de LocalStack directamente; usar un backend proxy
```

### 5.4 Validación de Input en todos los límites

- ✅ `InputMessage.isValid` — ya implementado
- ✅ StreamWriter — validación cliente
- Verificar que la Lambda nunca hace SQL/NoSQL injection (usar query builders, no interpolación)

---

## 6. Cobertura de Tests

### Objetivo: ≥ 80% en el pipeline

```bash
# Scala: con sbt-scoverage
sbt coverage test coverageReport
# Reporte en: target/scala-3.x/scoverage-report/index.html

# Node.js (dashboard + value-backend)
npm run test -- --coverage
```

Añadir en `build.sbt`:
```sbt
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.1.1")
```

### Suite de tests de carga

```scala
// pipeline/src/test/scala/LoadSpec.scala
object LoadSpec extends ZIOSpecDefault:
  def spec = suite("Load Test")(
    test("procesa 500 mensajes sin errores") {
      val messages = (1 to 500).map { i =>
        InputMessage(s"node-$i", Instant.now(), Some(s"reg-$i"), None)
      }.toList

      for
        processor <- ZIO.service[BatchProcessor]
        result    <- processor.processBatch(messages)
      yield
        assertTrue(result.failed == 0) &&
        assertTrue(result.succeeded == 500)
    }
  ).provide(/* all layers */) @@ TestAspect.timeout(60.seconds)
```

---

## 7. Documentación Final

### Checklist de documentación

- [ ] `README.md` — instrucciones de despliegue actualizadas con pasos de producción
- [ ] `value-backend/README.md` — contrato de API completo
- [ ] `pipeline/README.md` — arquitectura, variables de entorno, empaquetado
- [ ] `dashboard/README.md` — guía de desarrollo y construcción
- [ ] `infra/README.md` — instrucciones de Terraform (local y producción)
- [ ] `docs/architecture.md` — diagrama de arquitectura detallado
- [ ] `CONTRIBUTING.md` — flujo de trabajo, convenciones de código, proceso de PR

### `CONTRIBUTING.md` (esquema)

```markdown
# Contributing

## Flujo de trabajo
- Ramas: `feature/<nombre>`, `fix/<nombre>`, `chore/<nombre>`
- PRs contra `main`, requieren 1 revisión
- Commits: Conventional Commits (feat, fix, chore, docs, test)

## Convenciones
- Scala: Scalafmt (config en .scalafmt.conf)
- TypeScript: ESLint + Prettier
- Commits con `git commit` (no --no-verify)

## Tests obligatorios
- Todo PR debe pasar `sbt test` y `npm test`
- Cobertura no debe bajar del 80%
```

---

## 8. Despliegue en AWS Real (opcional)

Cuando se desee desplegar en AWS real en lugar de LocalStack:

```bash
# 1. Configurar credenciales AWS reales
aws configure

# 2. Actualizar terraform.tfvars para apuntar a AWS real
# (eliminar los endpoints de localhost)

# 3. Inicializar con backend remoto (S3)
terraform init -backend-config="bucket=my-tf-state" -backend-config="key=pipeline/terraform.tfstate"

# 4. Planificar y aplicar
terraform plan -out=tfplan
terraform apply tfplan
```

---

## Criterios de Aceptación

- [ ] Script `e2e-smoke.sh` pasa sin errores
- [ ] Test de lote con 500 mensajes completa sin errores de procesamiento
- [ ] DLQ configurada y mensajes fallidos llegan correctamente
- [ ] Alarmas CloudWatch definidas en Terraform
- [ ] IAM roles usan principio de menor privilegio
- [ ] Credenciales DB gestionadas via Secrets Manager (o variables de entorno en local)
- [ ] Cobertura de tests ≥ 80% en el pipeline
- [ ] Toda la documentación listada en el checklist está actualizada
- [ ] `CONTRIBUTING.md` existe con guías claras
- [ ] El proyecto arranca limpio desde cero con los comandos del README
