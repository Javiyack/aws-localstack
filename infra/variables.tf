variable "project_name" {
  description = "Nombre del proyecto (prefijo para recursos AWS)"
  type        = string
  default     = "pipeline"
}

variable "environment" {
  description = "Entorno de despliegue"
  type        = string
  default     = "local"
}

variable "aws_region" {
  description = "Región AWS"
  type        = string
  default     = "us-east-1"
}

variable "localstack" {
  description = "Si true, apunta todos los endpoints a LocalStack en localhost:4566"
  type        = bool
  default     = true
}

variable "input_stream_name" {
  description = "Nombre del stream Kinesis de entrada"
  type        = string
  default     = "input-stream"
}

variable "output_stream_name" {
  description = "Nombre del stream Kinesis de salida"
  type        = string
  default     = "output-stream"
}

variable "shard_count" {
  description = "Número de shards por stream Kinesis"
  type        = number
  default     = 1
}

variable "dynamodb_table_name" {
  description = "Nombre de la tabla DynamoDB de auditoría"
  type        = string
  default     = "audit-records"
}

variable "redis_host" {
  description = "Host de Redis"
  type        = string
  default     = "pipeline-redis"
}

variable "redis_port" {
  description = "Puerto de Redis"
  type        = number
  default     = 6379
}

variable "postgres_host" {
  description = "Host de PostgreSQL"
  type        = string
  default     = "pipeline-postgres"
}

variable "postgres_port" {
  description = "Puerto de PostgreSQL"
  type        = number
  default     = 5432
}

variable "postgres_database" {
  description = "Nombre de la base de datos PostgreSQL"
  type        = string
  default     = "pipeline"
}

variable "postgres_user" {
  description = "Usuario de PostgreSQL"
  type        = string
  default     = "pipeline"
  sensitive   = true
}

variable "postgres_password" {
  description = "Contraseña de PostgreSQL"
  type        = string
  default     = "pipeline"
  sensitive   = true
}

variable "value_backend_url" {
  description = "URL base del Value Backend"
  type        = string
  default     = "http://value-backend:3333"
}
