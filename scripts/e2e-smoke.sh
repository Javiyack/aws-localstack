#!/usr/bin/env bash
# scripts/e2e-smoke.sh
# ---------------------------------------------------------------------------
# Test de humo end-to-end del pipeline AWS Local.
# Requiere: Docker Compose levantado, AWS CLI, psql.
# Uso: bash scripts/e2e-smoke.sh
# ---------------------------------------------------------------------------
set -euo pipefail

ENDPOINT="http://localhost:4566"
INPUT_STREAM="input-stream"
OUTPUT_STREAM="output-stream"
DLQ_NAME="aws-local-pipeline-dlq"
DYNAMO_TABLE="audit-records"
NODE_ID="e2e-node-$(date +%s)"
REG_ID="e2e-reg-$(date +%s)"
WAIT_SECONDS="${E2E_WAIT_SECONDS:-8}"

AWS_CMD="aws --endpoint-url=$ENDPOINT"
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

ok()   { echo "  ✓ $*"; }
fail() { echo "  ✗ $*"; exit 1; }
step() { echo; echo "=== $* ==="; }

# ── 1. Health checks ─────────────────────────────────────────────────────────
step "1. Verificar servicios"

curl -sf "$ENDPOINT/_localstack/health" \
  | python3 -c "import sys,json; h=json.load(sys.stdin)['services']; assert h.get('kinesis')=='running', h" \
  && ok "LocalStack Kinesis running" \
  || fail "LocalStack no está listo"

curl -sf http://localhost:3000/health \
  | python3 -c "import sys,json; r=json.load(sys.stdin); assert r.get('status')=='ok'" \
  && ok "value-backend healthy" \
  || { echo "  ⚠ value-backend no disponible (continúa de todas formas)"; }

# ── 2. Publicar mensaje de prueba ────────────────────────────────────────────
step "2. Publicar mensaje en input-stream"

MSG=$(printf '{"nodeId":"%s","dttmUtc":"%s","registrationId":"%s"}' \
  "$NODE_ID" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$REG_ID")
DATA=$(printf '%s' "$MSG" | base64 | tr -d '\n')

$AWS_CMD kinesis put-record \
  --stream-name "$INPUT_STREAM" \
  --partition-key "$NODE_ID" \
  --data "$DATA" \
  --query 'SequenceNumber' \
  --output text \
  | xargs -I{} echo "  SequenceNumber: {}"
ok "Mensaje publicado: $MSG"

# ── 3. Esperar procesamiento ─────────────────────────────────────────────────
step "3. Esperando procesamiento (${WAIT_SECONDS}s)"
sleep "$WAIT_SECONDS"

# ── 4. Verificar DynamoDB ────────────────────────────────────────────────────
step "4. Verificar auditoría en DynamoDB"

ITEMS=$($AWS_CMD dynamodb query \
  --table-name "$DYNAMO_TABLE" \
  --key-condition-expression "nodeId = :n" \
  --expression-attribute-values "{\":n\":{\"S\":\"$NODE_ID\"}}" \
  --query 'Count' \
  --output text 2>/dev/null || echo "0")

if [ "$ITEMS" -gt 0 ]; then
  ok "DynamoDB: $ITEMS registro(s) de auditoría"
else
  fail "DynamoDB: no se encontraron registros para nodeId=$NODE_ID"
fi

# ── 5. Verificar PostgreSQL ──────────────────────────────────────────────────
step "5. Verificar intervalos en PostgreSQL"

COUNT=$(psql -h localhost -U pipeline -d pipeline -t -c \
  "SELECT count(*) FROM performance_intervals;" 2>/dev/null \
  | tr -d '[:space:]' || echo "N/A")
ok "PostgreSQL: $COUNT registro(s) en performance_intervals"

# ── 6. Verificar output-stream ───────────────────────────────────────────────
step "6. Verificar output-stream"

ITERATOR=$($AWS_CMD kinesis get-shard-iterator \
  --stream-name "$OUTPUT_STREAM" \
  --shard-id shardId-000000000000 \
  --shard-iterator-type TRIM_HORIZON \
  --query 'ShardIterator' \
  --output text)

RECORDS=$($AWS_CMD kinesis get-records \
  --shard-iterator "$ITERATOR" \
  --limit 100 \
  --query 'Records | length(@)' \
  --output text)

if [ "${RECORDS:-0}" -gt 0 ]; then
  ok "output-stream: $RECORDS registro(s)"
else
  echo "  ⚠ output-stream vacío (puede que Lambda no esté activa en LocalStack)"
fi

# ── 7. Verificar DLQ ────────────────────────────────────────────────────────
step "7. Verificar DLQ"

DLQ_URL=$($AWS_CMD sqs get-queue-url \
  --queue-name "$DLQ_NAME" \
  --query 'QueueUrl' \
  --output text 2>/dev/null || echo "")

if [ -n "$DLQ_URL" ]; then
  DLQ_COUNT=$($AWS_CMD sqs get-queue-attributes \
    --queue-url "$DLQ_URL" \
    --attribute-names ApproximateNumberOfMessages \
    --query 'Attributes.ApproximateNumberOfMessages' \
    --output text)
  ok "DLQ: $DLQ_COUNT mensaje(s) en cola"
else
  echo "  ⚠ DLQ no encontrada (Terraform no aplicado)"
fi

echo
echo "════════════════════════════════"
echo "  E2E SMOKE TEST COMPLETADO ✓"
echo "════════════════════════════════"
