# Sprint 0 — Fundamentos & Entorno de Desarrollo

## Objetivo

Tener el entorno de desarrollo completamente operativo y la infraestructura local levantada antes de escribir una sola línea de lógica de negocio.

**Duración estimada:** 1 semana  
**Dependencias previas:** ninguna

---

## 1. Estructura del Monorepo

Crear la siguiente estructura de carpetas en la raíz del proyecto:

```
aws-localstack/
├── pipeline/          # Backend Scala/ZIO
├── value-backend/     # Servicio HTTP de valores
├── dashboard/         # Frontend React/TypeScript
├── infra/             # Terraform + LocalStack
├── docs/
│   └── sprints/       # Guías de implementación (este directorio)
├── .gitignore
├── README.md
└── TODO.md
```

```bash
mkdir pipeline value-backend dashboard infra
```

---

## 2. `.gitignore`

Crear un `.gitignore` en la raíz que cubra todos los subproyectos:

```gitignore
# Scala / SBT
target/
project/target/
project/project/
*.class
*.jar
.bsp/
.metals/
.idea/

# Node.js
node_modules/
dist/
build/
.env
.env.local
.env.*.local
npm-debug.log*

# Terraform
**/.terraform/
*.tfstate
*.tfstate.backup
*.tfvars
!terraform.tfvars.example
.terraform.lock.hcl

# Docker
.docker/

# OS
.DS_Store
Thumbs.db
```

---

## 3. Docker & LocalStack

### 3.1 Verificar Docker
```bash
docker --version
docker ps
```

### 3.2 Arrancar LocalStack
```bash
docker run --rm -d \
  --name localstack \
  -p 4566:4566 \
  -e SERVICES=kinesis,dynamodb,lambda,cloudwatch,s3 \
  -e DEFAULT_REGION=us-east-1 \
  -e DEBUG=1 \
  localstack/localstack:latest
```

### 3.3 Verificar que LocalStack responde
```bash
curl http://localhost:4566/_localstack/health
```

Respuesta esperada: todos los servicios configurados en estado `"running"`.

---

## 4. Terraform — Infraestructura Local

### 4.1 Estructura del módulo Terraform

```
infra/
├── main.tf
├── variables.tf
├── outputs.tf
├── terraform.tfvars         # valores locales (en .gitignore)
├── terraform.tfvars.example # plantilla versionada
└── modules/
    ├── kinesis/
    ├── dynamodb/
    └── lambda/
```

### 4.2 `infra/main.tf`

```hcl
terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    kinesis    = "http://localhost:4566"
    dynamodb   = "http://localhost:4566"
    lambda     = "http://localhost:4566"
    cloudwatch = "http://localhost:4566"
    iam        = "http://localhost:4566"
  }
}

# ── Kinesis ──────────────────────────────────────────────────
resource "aws_kinesis_stream" "input" {
  name             = "input-stream"
  shard_count      = 1
  retention_period = 24
}

resource "aws_kinesis_stream" "output" {
  name             = "output-stream"
  shard_count      = 1
  retention_period = 24
}

# ── DynamoDB — Auditoría ─────────────────────────────────────
resource "aws_dynamodb_table" "audit" {
  name           = "audit-records"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "node_id"
  range_key      = "dttm_utc"

  attribute {
    name = "node_id"
    type = "S"
  }

  attribute {
    name = "dttm_utc"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }
}

# ── IAM Role para Lambda ─────────────────────────────────────
resource "aws_iam_role" "lambda_role" {
  name = "pipeline-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })
}

# ── Lambda — placeholder (se reemplaza en Sprint 4) ──────────
resource "aws_lambda_function" "pipeline" {
  function_name = "pipeline-processor"
  role          = aws_iam_role.lambda_role.arn
  handler       = "bootstrap"
  runtime       = "java21"
  filename      = "../pipeline/target/scala-3.x/pipeline-assembly.jar"

  environment {
    variables = {
      INPUT_STREAM_NAME  = aws_kinesis_stream.input.name
      OUTPUT_STREAM_NAME = aws_kinesis_stream.output.name
      DYNAMODB_TABLE     = aws_dynamodb_table.audit.name
      REDIS_HOST         = "localhost"
      REDIS_PORT         = "6379"
      DB_HOST            = "localhost"
      DB_PORT            = "5432"
      DB_NAME            = "pipeline"
      VALUE_BACKEND_URL  = "http://localhost:3333"
    }
  }

  depends_on = [aws_iam_role.lambda_role]
}

# ── Trigger: Kinesis → Lambda ─────────────────────────────────
resource "aws_lambda_event_source_mapping" "kinesis_trigger" {
  event_source_arn              = aws_kinesis_stream.input.arn
  function_name                 = aws_lambda_function.pipeline.arn
  starting_position             = "LATEST"
  batch_size                    = 500
  bisect_batch_on_function_error = true
}
```

### 4.3 `infra/outputs.tf`

```hcl
output "input_stream_arn"  { value = aws_kinesis_stream.input.arn }
output "output_stream_arn" { value = aws_kinesis_stream.output.arn }
output "dynamodb_table"    { value = aws_dynamodb_table.audit.name }
output "lambda_arn"        { value = aws_lambda_function.pipeline.arn }
```

### 4.4 Inicializar y aplicar

```bash
cd infra
terraform init
terraform plan
terraform apply -auto-approve
```

---

## 5. Servicios de Soporte con Docker Compose

Crear `docker-compose.yml` en la raíz para Redis y PostgreSQL:

```yaml
version: "3.9"
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes

  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB:       pipeline
      POSTGRES_USER:     pipeline
      POSTGRES_PASSWORD: pipeline
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

```bash
docker compose up -d
```

---

## 6. Verificación Final del Sprint 0

| Servicio | Verificación |
|---|---|
| LocalStack | `curl http://localhost:4566/_localstack/health` |
| Redis | `docker exec -it <redis-container> redis-cli ping` → `PONG` |
| PostgreSQL | `psql -h localhost -U pipeline -d pipeline -c "\l"` |
| Kinesis input | `aws --endpoint-url=http://localhost:4566 kinesis describe-stream --stream-name input-stream` |
| Kinesis output | `aws --endpoint-url=http://localhost:4566 kinesis describe-stream --stream-name output-stream` |
| DynamoDB | `aws --endpoint-url=http://localhost:4566 dynamodb describe-table --table-name audit-records` |

---

## Criterios de Aceptación

- [ ] `terraform apply` finaliza sin errores
- [ ] Los 6 servicios de la tabla de verificación responden correctamente
- [ ] La estructura de carpetas del monorepo está creada
- [ ] `.gitignore` cubre Scala, Node.js y Terraform
- [ ] El primer commit con la estructura base está en el repositorio remoto
