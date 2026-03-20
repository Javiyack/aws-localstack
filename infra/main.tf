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
  region                      = var.aws_region
  access_key                  = var.localstack ? "test" : null
  secret_key                  = var.localstack ? "test" : null
  skip_credentials_validation = var.localstack
  skip_metadata_api_check     = var.localstack
  skip_requesting_account_id  = var.localstack
  s3_use_path_style           = var.localstack

  dynamic "endpoints" {
    for_each = var.localstack ? [1] : []
    content {
      kinesis          = "http://localhost:4566"
      dynamodb         = "http://localhost:4566"
      lambda           = "http://localhost:4566"
      cloudwatch       = "http://localhost:4566"
      cloudwatchlogs   = "http://localhost:4566"
      sqs              = "http://localhost:4566"
      iam              = "http://localhost:4566"
      secretsmanager   = "http://localhost:4566"
      s3               = "http://localhost:4566"
    }
  }
}

# ── Kinesis ──────────────────────────────────────────────────
resource "aws_kinesis_stream" "input" {
  name             = var.input_stream_name
  shard_count      = var.shard_count
  retention_period = 24

  tags = local.common_tags
}

resource "aws_kinesis_stream" "output" {
  name             = var.output_stream_name
  shard_count      = var.shard_count
  retention_period = 24

  tags = local.common_tags
}

# ── DynamoDB — Auditoría ─────────────────────────────────────
resource "aws_dynamodb_table" "audit" {
  name         = var.dynamodb_table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "nodeId"
  range_key    = "dttmUtc"

  attribute {
    name = "nodeId"
    type = "S"
  }

  attribute {
    name = "dttmUtc"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  tags = local.common_tags
}

# ── SQS Dead Letter Queue ─────────────────────────────────────
resource "aws_sqs_queue" "dlq" {
  name                       = "${var.project_name}-dlq"
  message_retention_seconds  = 1209600  # 14 días

  tags = local.common_tags
}

# ── IAM Role para Lambda ─────────────────────────────────────
resource "aws_iam_role" "lambda_role" {
  name = "${var.project_name}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy" "lambda_policy" {
  name   = "${var.project_name}-lambda-policy"
  role   = aws_iam_role.lambda_role.id
  policy = data.aws_iam_policy_document.lambda_permissions.json
}

data "aws_iam_policy_document" "lambda_permissions" {
  # Kinesis — solo lectura del input, solo escritura al output
  statement {
    actions = [
      "kinesis:GetRecords",
      "kinesis:GetShardIterator",
      "kinesis:DescribeStream",
      "kinesis:ListShards",
      "kinesis:ListStreams",
    ]
    resources = [aws_kinesis_stream.input.arn]
  }

  statement {
    actions   = ["kinesis:PutRecord", "kinesis:PutRecords"]
    resources = [aws_kinesis_stream.output.arn]
  }

  # DynamoDB — solo escritura a la tabla de auditoría
  statement {
    actions   = ["dynamodb:PutItem", "dynamodb:UpdateItem"]
    resources = [aws_dynamodb_table.audit.arn]
  }

  # SQS DLQ
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.dlq.arn]
  }

  # CloudWatch Logs
  statement {
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["arn:aws:logs:*:*:/aws/lambda/${var.project_name}-*"]
  }

  # S3 — leer artefacto Lambda
  statement {
    actions   = ["s3:GetObject"]
    resources = ["arn:aws:s3:::${var.project_name}-lambda-artifacts/*"]
  }
}

# ── S3 bucket — artefactos Lambda ──────────────────────────────
resource "aws_s3_bucket" "lambda_artifacts" {
  bucket        = "${var.project_name}-lambda-artifacts"
  force_destroy = true

  tags = local.common_tags
}

resource "aws_s3_object" "lambda_jar" {
  count  = fileexists(local.lambda_jar_path) ? 1 : 0
  bucket = aws_s3_bucket.lambda_artifacts.id
  key    = "pipeline-assembly.jar"
  source = local.lambda_jar_path
  etag   = fileexists(local.lambda_jar_path) ? filemd5(local.lambda_jar_path) : null
}

# ── Lambda Function ───────────────────────────────────────────
resource "aws_lambda_function" "pipeline" {
  function_name = "${var.project_name}-processor"
  role          = aws_iam_role.lambda_role.arn
  handler       = "com.pipeline.LambdaHandler"
  runtime       = "java21"
  timeout       = 300
  memory_size   = 512

  s3_bucket        = aws_s3_bucket.lambda_artifacts.id
  s3_key           = "pipeline-assembly.jar"
  source_code_hash = fileexists(local.lambda_jar_path) ? filebase64sha256(local.lambda_jar_path) : null

  environment {
    variables = {
      KINESIS_ENDPOINT          = var.localstack ? "http://localhost:4566" : ""
      KINESIS_REGION            = var.aws_region
      KINESIS_INPUT_STREAM_NAME = aws_kinesis_stream.input.name
      KINESIS_OUTPUT_STREAM_NAME = aws_kinesis_stream.output.name
      DYNAMO_ENDPOINT           = var.localstack ? "http://localhost:4566" : ""
      DYNAMO_REGION             = var.aws_region
      DYNAMO_TABLE_NAME         = aws_dynamodb_table.audit.name
      REDIS_HOST                = var.redis_host
      REDIS_PORT                = tostring(var.redis_port)
      POSTGRES_HOST             = var.postgres_host
      POSTGRES_PORT             = tostring(var.postgres_port)
      POSTGRES_DATABASE         = var.postgres_database
      POSTGRES_USER             = var.postgres_user
      POSTGRES_PASSWORD         = var.postgres_password
      VALUE_BACKEND_BASE_URL    = var.value_backend_url
    }
  }

  tags = local.common_tags

  depends_on = [aws_iam_role_policy.lambda_policy, aws_s3_object.lambda_jar]
}

# ── Trigger: Kinesis → Lambda ─────────────────────────────────
resource "aws_lambda_event_source_mapping" "kinesis_trigger" {
  event_source_arn               = aws_kinesis_stream.input.arn
  function_name                  = aws_lambda_function.pipeline.arn
  starting_position              = "LATEST"
  batch_size                     = 500
  bisect_batch_on_function_error = true
  maximum_retry_attempts         = 3

  destination_config {
    on_failure {
      destination_arn = aws_sqs_queue.dlq.arn
    }
  }
}

# ── CloudWatch Alarmas ────────────────────────────────────────
resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "${var.project_name}-lambda-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "La Lambda de pipeline tiene una tasa de errores elevada (>10 en 2 min)"

  dimensions = {
    FunctionName = aws_lambda_function.pipeline.function_name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "lambda_duration" {
  alarm_name          = "${var.project_name}-lambda-duration"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Duration"
  namespace           = "AWS/Lambda"
  period              = 60
  extended_statistic  = "p99"
  threshold           = 10000  # 10 segundos en ms
  alarm_description   = "Latencia p99 de Lambda supera 10s"

  dimensions = {
    FunctionName = aws_lambda_function.pipeline.function_name
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_metric_alarm" "dlq_messages" {
  alarm_name          = "${var.project_name}-dlq-depth"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Hay mensajes en la DLQ (fallos no recuperables)"

  dimensions = {
    QueueName = aws_sqs_queue.dlq.name
  }

  tags = local.common_tags
}

# ── Secrets Manager — credenciales DB ────────────────────────
resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.project_name}/db-credentials"
  description = "Credenciales PostgreSQL del pipeline"

  tags = local.common_tags
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.postgres_user
    password = var.postgres_password
    host     = var.postgres_host
    port     = var.postgres_port
    dbname   = var.postgres_database
  })
}

# ── Locals ────────────────────────────────────────────────────
locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  lambda_jar_path = "${path.module}/../pipeline/target/scala-3.5.2/pipeline-assembly.jar"
}
