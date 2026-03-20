output "input_stream_name" {
  description = "Nombre del stream Kinesis de entrada"
  value       = aws_kinesis_stream.input.name
}

output "input_stream_arn" {
  description = "ARN del stream Kinesis de entrada"
  value       = aws_kinesis_stream.input.arn
}

output "output_stream_name" {
  description = "Nombre del stream Kinesis de salida"
  value       = aws_kinesis_stream.output.name
}

output "output_stream_arn" {
  description = "ARN del stream Kinesis de salida"
  value       = aws_kinesis_stream.output.arn
}

output "dynamodb_table_name" {
  description = "Nombre de la tabla DynamoDB de auditoría"
  value       = aws_dynamodb_table.audit.name
}

output "lambda_function_name" {
  description = "Nombre de la función Lambda"
  value       = aws_lambda_function.pipeline.function_name
}

output "lambda_function_arn" {
  description = "ARN de la función Lambda"
  value       = aws_lambda_function.pipeline.arn
}

output "dlq_url" {
  description = "URL de la Dead Letter Queue SQS"
  value       = aws_sqs_queue.dlq.url
}

output "dlq_arn" {
  description = "ARN de la Dead Letter Queue SQS"
  value       = aws_sqs_queue.dlq.arn
}
