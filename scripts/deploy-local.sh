#!/usr/bin/env bash
# deploy-local.sh — Despliega toda la infraestructura local y arranca los servicios.
# Uso: bash scripts/deploy-local.sh [--skip-build] [--skip-tf]
set -euo pipefail

# ── Colores ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[OK]${NC}  $*"; }
info() { echo -e "${CYAN}[..] $*${NC}"; }
warn() { echo -e "${YELLOW}[!!]${NC}  $*"; }
fail() { echo -e "${RED}[ERR]${NC} $*" >&2; exit 1; }

# ── Argumentos ────────────────────────────────────────────────────────────────
SKIP_BUILD=false
SKIP_TF=false
for arg in "$@"; do
  case $arg in
    --skip-build) SKIP_BUILD=true ;;
    --skip-tf)    SKIP_TF=true    ;;
  esac
done

# ── Raíz del proyecto ─────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# ── 0. Prerrequisitos ─────────────────────────────────────────────────────────
info "Comprobando prerrequisitos..."
command -v docker    >/dev/null || fail "docker no encontrado"
command -v terraform >/dev/null || fail "terraform no encontrado"
command -v node      >/dev/null || fail "node no encontrado"

# sbt es opcional (solo necesario si se va a compilar el pipeline)
SBT_AVAILABLE=false
command -v sbt >/dev/null 2>&1 && SBT_AVAILABLE=true

ok "Prerrequisitos OK"

# ── 1. Build Lambda JAR (sbt assembly) ───────────────────────────────────────
JAR_PATH="$ROOT/pipeline/target/scala-3.5.2/pipeline-assembly.jar"

if $SKIP_BUILD; then
  warn "Saltando build del JAR (--skip-build)"
elif ! $SBT_AVAILABLE; then
  warn "sbt no disponible — saltando build del JAR"
else
  info "Compilando Lambda JAR con sbt assembly..."
  (cd "$ROOT/pipeline" && sbt -no-server assembly)
  ok "JAR compilado: $JAR_PATH"
fi

if [[ ! -f "$JAR_PATH" ]]; then
  warn "JAR no encontrado en $JAR_PATH — Lambda no se actualizará en LocalStack"
fi

# ── 2. Levantar servicios Docker Compose ─────────────────────────────────────
info "Levantando servicios con Docker Compose..."
docker compose up -d --build

info "Esperando que LocalStack esté listo..."
for i in $(seq 1 30); do
  if curl -sf http://localhost:4566/_localstack/health | grep -q '"kinesis": "running"'; then
    ok "LocalStack listo"
    break
  fi
  [[ $i -eq 30 ]] && fail "LocalStack no respondió en 30 intentos"
  sleep 3
done

info "Esperando que PostgreSQL esté listo..."
for i in $(seq 1 20); do
  if docker exec pipeline-postgres pg_isready -U pipeline -d pipeline >/dev/null 2>&1; then
    ok "PostgreSQL listo"
    break
  fi
  [[ $i -eq 20 ]] && fail "PostgreSQL no respondió en 20 intentos"
  sleep 3
done

info "Esperando que Redis esté listo..."
for i in $(seq 1 20); do
  if docker exec pipeline-redis redis-cli ping 2>/dev/null | grep -q PONG; then
    ok "Redis listo"
    break
  fi
  [[ $i -eq 20 ]] && fail "Redis no respondió en 20 intentos"
  sleep 2
done

# ── 3. Terraform — crear recursos en LocalStack ───────────────────────────────
if $SKIP_TF; then
  warn "Saltando Terraform (--skip-tf)"
else
  info "Aplicando Terraform..."
  (cd "$ROOT/infra" && terraform init -input=false -reconfigure > /tmp/tf-init.log 2>&1) \
    || { cat /tmp/tf-init.log; fail "terraform init falló"; }
  (cd "$ROOT/infra" && terraform apply -auto-approve -input=false 2>&1 | tee /tmp/tf-apply.log) \
    || fail "terraform apply falló"
  ok "Terraform aplicado"
fi

# ── 4. Resumen de servicios ───────────────────────────────────────────────────
echo ""
echo -e "${GREEN}════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Entorno local levantado correctamente ✓${NC}"
echo -e "${GREEN}════════════════════════════════════════════${NC}"
echo ""
echo -e "  LocalStack   → http://localhost:4566"
echo -e "  Value-backend→ http://localhost:3333"
echo -e "  Dashboard    → http://localhost:5173"
echo -e "  PostgreSQL   → localhost:5432  (pipeline/pipeline)"
echo -e "  Redis        → localhost:6379"
echo ""
echo -e "  Smoke test:  bash scripts/e2e-smoke.sh"
echo -e "  Detener:     docker compose down"
echo ""
