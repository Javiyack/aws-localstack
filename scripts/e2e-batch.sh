#!/usr/bin/env bash
# scripts/e2e-batch.sh
# ---------------------------------------------------------------------------
# Test de carga: envía N mensajes al pipeline y verifica los resultados.
# Uso: bash scripts/e2e-batch.sh [total_mensajes] [concurrencia]
# ---------------------------------------------------------------------------
set -euo pipefail

ENDPOINT="http://localhost:4566"
INPUT_STREAM="input-stream"
TOTAL="${1:-500}"
CONCURRENCY="${2:-20}"
WAIT_SECONDS="${E2E_WAIT_SECONDS:-15}"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

AWS_CMD="aws --endpoint-url=$ENDPOINT"
SENT=0
ERRORS=0
RUN_ID="batch-$(date +%s)"

echo "╔══════════════════════════════════════╗"
echo "║  E2E Batch Test — $TOTAL mensajes     "
echo "║  run_id=$RUN_ID"
echo "╚══════════════════════════════════════╝"

send_message() {
  local i=$1
  local type=$((RANDOM % 2))
  local node_id
  node_id=$(printf "node-%03d" $((RANDOM % 20 + 1)))
  local dttm
  dttm=$(date -u +%Y-%m-%dT%H:%M:%SZ)

  if [ $type -eq 0 ]; then
    local id_field
    id_field=$(printf '"registrationId":"reg-%03d"' $((RANDOM % 10 + 1)))
  else
    local id_field
    id_field=$(printf '"baselineId":"base-%03d"' $((RANDOM % 10 + 1)))
  fi

  local msg="{\"nodeId\":\"$node_id\",\"dttmUtc\":\"$dttm\",$id_field}"
  local data
  data=$(printf '%s' "$msg" | base64 | tr -d '\n')

  $AWS_CMD kinesis put-record \
    --stream-name "$INPUT_STREAM" \
    --partition-key "$node_id" \
    --data "$data" \
    --output text \
    --query 'SequenceNumber' > /dev/null 2>&1
}

export -f send_message
export ENDPOINT INPUT_STREAM AWS_CMD

echo
echo "▶ Enviando $TOTAL mensajes (concurrencia: $CONCURRENCY)..."
START_TS=$(date +%s%3N)

seq 1 "$TOTAL" | \
  xargs -P "$CONCURRENCY" -I{} bash -c 'send_message "$@"' _ {} \
  && SENT=$TOTAL \
  || { ERRORS=$((TOTAL - SENT)); }

END_TS=$(date +%s%3N)
ELAPSED=$(( (END_TS - START_TS) ))
RATE=$(( TOTAL * 1000 / (ELAPSED + 1) ))

echo
echo "┌─────────────────────────────────────┐"
printf "│  Enviados:   %-5d                   │\n" "$SENT"
printf "│  Errores:    %-5d                   │\n" "$ERRORS"
printf "│  Tiempo:     %sms                  \n"  "$ELAPSED"
printf "│  Rate:       %s msg/s               \n" "$RATE"
echo "└─────────────────────────────────────┘"

echo
echo "⏳ Esperando procesamiento Lambda (${WAIT_SECONDS}s)..."
sleep "$WAIT_SECONDS"

echo
echo "=== Verificación post-batch ==="

# DynamoDB count
DYNAMO_COUNT=$($AWS_CMD dynamodb scan \
  --table-name audit-records \
  --select COUNT \
  --query 'Count' \
  --output text 2>/dev/null || echo "N/A")
echo "  DynamoDB audit-records: $DYNAMO_COUNT registros totales"

# PostgreSQL count
PG_COUNT=$(psql -h localhost -U pipeline -d pipeline -t -c \
  "SELECT count(*) FROM performance_intervals;" 2>/dev/null \
  | tr -d '[:space:]' || echo "N/A")
echo "  PostgreSQL performance_intervals: $PG_COUNT registros totales"

# DLQ
DLQ_URL=$($AWS_CMD sqs get-queue-url \
  --queue-name "aws-local-pipeline-dlq" \
  --query 'QueueUrl' \
  --output text 2>/dev/null || echo "")
if [ -n "$DLQ_URL" ]; then
  DLQ_COUNT=$($AWS_CMD sqs get-queue-attributes \
    --queue-url "$DLQ_URL" \
    --attribute-names ApproximateNumberOfMessages \
    --query 'Attributes.ApproximateNumberOfMessages' \
    --output text)
  echo "  DLQ mensajes fallidos: $DLQ_COUNT"
  if [ "${DLQ_COUNT:-0}" -gt 0 ]; then
    echo "  ⚠ Hay mensajes en la DLQ. Revisar logs de Lambda."
  fi
fi

echo
echo "  ✓ Batch test completado (run_id=$RUN_ID)"
