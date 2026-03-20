# Infraestructura — Terraform + LocalStack

Provisiona todos los recursos AWS mediante Terraform, apuntando a **LocalStack** en local o a **AWS real** en producción.

## Recursos gestionados

| Recurso | Nombre | Descripción |
|---|---|---|
| Kinesis Stream | `input-stream` | Mensajes de entrada |
| Kinesis Stream | `output-stream` | Mensajes procesados |
| DynamoDB Table | `audit-records` | Auditoría con TTL 24h |
| Lambda Function | `aws-local-pipeline-processor` | Procesador principal |
| SQS Queue (DLQ) | `aws-local-pipeline-dlq` | Mensajes fallidos (14 días) |
| IAM Role | `aws-local-pipeline-lambda-role` | Rol de la Lambda |
| CloudWatch Alarm | `*-lambda-errors` | Errores > 10 en 2 min |
| CloudWatch Alarm | `*-lambda-duration` | Latencia p99 > 10s |
| CloudWatch Alarm | `*-dlq-depth` | Mensajes en DLQ > 0 |
| Secrets Manager | `aws-local/db-credentials` | Credenciales PostgreSQL |

## Uso local (LocalStack)

```bash
cd infra

# 1. Inicializar
terraform init

# 2. Revisar plan
terraform plan

# 3. Aplicar (LocalStack debe estar corriendo)
terraform apply -auto-approve

# 4. Ver outputs
terraform output
```

## Uso en AWS real

```bash
# Copiar y adaptar variables
cp terraform.tfvars.example terraform.tfvars
# Editar terraform.tfvars: poner localstack = false + credenciales reales

# Inicializar con backend remoto S3 (recomendado)
terraform init \
  -backend-config="bucket=mi-tf-state" \
  -backend-config="key=pipeline/terraform.tfstate" \
  -backend-config="region=us-east-1"

terraform plan -out=tfplan
terraform apply tfplan
```

## Variables principales

Ver [variables.tf](variables.tf) para la lista completa.

| Variable | Por defecto | Descripción |
|---|---|---|
| `localstack` | `true` | Usar endpoints de LocalStack |
| `aws_region` | `us-east-1` | Región AWS |
| `project_name` | `aws-local-pipeline` | Prefijo de todos los recursos |
| `shard_count` | `1` | Shards por stream Kinesis |
