# 📋 TODO — Plan de Desarrollo por Sprints

Hoja de ruta completa para el desarrollo del **Ecosistema de Procesamiento de Datos AWS**.  
Cada sprint tiene una duración estimada de **1 semana**.

---

## Estado de las Tareas

| Símbolo | Significado |
|---|---|
| `[ ]` | Pendiente |
| `[x]` | Completado |
| `[-]` | En progreso |
| `[~]` | Bloqueado / en espera |

---

## 🏁 Sprint 0 — Fundamentos & Entorno de Desarrollo

> Objetivo: tener el entorno completamente operativo antes de escribir una sola línea de lógica de negocio.

### Repositorio & Estructura
- [x] Inicializar repositorio Git
- [x] Crear repositorio en GitHub (`aws-localstack`)
- [x] Redactar `README.md`
- [ ] Crear estructura de carpetas del monorepo (`pipeline/`, `value-backend/`, `dashboard/`, `infra/`)
- [ ] Añadir `.gitignore` adecuado para Scala, Node.js y Terraform

### Infraestructura Local (IaC)
- [ ] Instalar y configurar `Docker Desktop`
- [ ] Verificar arranque de `LocalStack` vía Docker
- [ ] Crear configuración inicial de `Terraform` para LocalStack:
  - [ ] Provider `aws` apuntando a `http://localhost:4566`
  - [ ] Recurso `aws_kinesis_stream` — stream de entrada (`input-stream`)
  - [ ] Recurso `aws_kinesis_stream` — stream de salida (`output-stream`)
  - [ ] Recurso `aws_dynamodb_table` — tabla de auditoría con TTL
  - [ ] Recurso `aws_lambda_function` — placeholder inicial
  - [ ] Outputs: ARNs y nombres de recursos
- [ ] Verificar plan y apply de Terraform sin errores

### Herramientas del proyecto
- [ ] Configurar `sbt` con versión de Scala 3.x
- [ ] Inicializar proyecto Node.js v22 en `value-backend/` y `dashboard/`
- [ ] Configurar `EditorConfig` y formatters (Scalafmt, Prettier, ESLint)

---

## 🟡 Sprint 1 — Value Backend

> Objetivo: tener operativo el servicio de valores que el pipeline consumirá.

- [ ] Crear proyecto `value-backend/` (Node.js + TypeScript o framework ligero)
- [ ] Implementar endpoint `GET /:id`
  - [ ] Devolver `{ "value": <Double aleatorio entre 900 y 1100> }`
  - [ ] El parámetro `:id` se recibe pero no altera la lógica (mock)
- [ ] Añadir manejo básico de errores (404 si `:id` está vacío)
- [ ] Escribir tests unitarios del endpoint
- [ ] Añadir `Dockerfile` para el servicio
- [ ] Verificar que responde en `http://localhost:3333/:id`
- [ ] Documentar en `value-backend/README.md`

---

## 🟠 Sprint 2 — Pipeline: Modelos & Configuración Base

> Objetivo: definir los modelos de datos y el esqueleto del proyecto Scala/ZIO.

### Proyecto Scala
- [ ] Configurar `build.sbt` con dependencias:
  - [ ] `zio`, `zio-streams`
  - [ ] `zio-aws-kinesis`, `zio-aws-dynamodb`, `zio-aws-cloudwatch`
  - [ ] `zio-redis`
  - [ ] `slick`, `postgresql` JDBC driver
  - [ ] `scanamo`
  - [ ] `sttp3` + backend async
  - [ ] `circe` (core, generic, parser)
  - [ ] `aws-lambda-java-core`, `aws-lambda-java-events`

### Modelos de Dominio
- [ ] Definir case class `InputMessage`:
  - `node_id: String`, `dttm_utc: Instant`, `registration_id: Option[String]`, `baseline_id: Option[String]`
  - Validación: exactamente uno de los dos opcionales presente
- [ ] Definir case class `PerformanceInterval`:
  - `dispatch_unit: String`, `node_id: String`, `dttm_utc: Instant`, `metered_value: Option[Double]`, `baseline_value: Option[Double]`, `baseline_id: Option[String]`
- [ ] Definir case class `AuditRecord` para DynamoDB
- [ ] Codecs Circe para todos los modelos
- [ ] Tests unitarios para validación de modelos y codecs

### Configuración
- [ ] Configurar `AppConfig` con ZIO Config (endpoints, credenciales LocalStack, nombres de streams/tabla/etc.)

---

## 🟧 Sprint 3 — Pipeline: Integraciones con AWS

> Objetivo: capas de acceso a cada servicio AWS, testadas de forma aislada.

### Kinesis
- [ ] Implementar `KinesisConsumer`: lectura de `input-stream` en lotes de 500
- [ ] Implementar `KinesisProducer`: escritura al `output-stream`
- [ ] Tests de integración con LocalStack

### DynamoDB — Auditoría
- [ ] Implementar `AuditRepository` con Scanamo
  - [ ] `save(record: AuditRecord): Task[Unit]`
  - [ ] TTL automático = `dttm_utc + 24h`
- [ ] Tests de integración con LocalStack

### Redis — Caché
- [ ] Implementar `PerformanceCache` con ZIO Redis
  - [ ] `upsert(interval: PerformanceInterval): Task[PerformanceInterval]`
  - Actualizar solo campos `Some`, preservar campos previos
  - Devolver el objeto merged completo
- [ ] Tests de integración con Redis local (Docker)

### PostgreSQL — Persistencia
- [ ] Definir schema SQL (`performance_intervals` table)
- [ ] Implementar `PerformanceRepository` con Slick
  - [ ] `upsert(interval: PerformanceInterval): Task[Unit]`
- [ ] Migraciones con Flyway o script SQL inicial
- [ ] Tests de integración con PostgreSQL local (Docker)

---

## 🔴 Sprint 4 — Pipeline: Lógica de Negocio & Lambda

> Objetivo: pipeline funcional end-to-end ejecutándose en LocalStack.

### HTTP Client — Value Backend
- [ ] Implementar `ValueClient` con sttp3
  - [ ] `getValue(id: String): Task[Double]`
  - [ ] Manejo de errores HTTP (timeout, 5xx, body inválido)
- [ ] Tests unitarios con mock server

### Orquestación del Pipeline
- [ ] Implementar `PipelineProcessor`:
  1. Decodificar `InputMessage` desde el evento Kinesis
  2. Guardar `AuditRecord` en DynamoDB
  3. Llamar a `ValueClient.getValue(registration_id | baseline_id)`
  4. Construir `PerformanceInterval` según el caso (metered vs baseline)
  5. Upsert en Redis → obtener objeto merged
  6. Persistir en PostgreSQL
  7. Publicar al `output-stream` de Kinesis
- [ ] Manejo de errores por mensaje (un fallo no detiene el lote)
- [ ] Logging estructurado con ZIO + CloudWatch

### AWS Lambda Handler
- [ ] Implementar `LambdaHandler` que recibe `KinesisEvent` de AWS SDK
- [ ] Inicialización del runtime ZIO en el handler
- [ ] Empaquetado con `sbt assembly` (fat JAR)
- [ ] Deploy en LocalStack via Terraform
- [ ] Test end-to-end: publicar mensaje al input-stream → verificar output-stream

---

## 🟣 Sprint 5 — Dashboard: Estructura & Panel de Estado

> Objetivo: proyecto React operativo con vista del estado de los recursos AWS.

### Setup Frontend
- [ ] Inicializar proyecto con Vite + React + TypeScript
- [ ] Configurar ESLint, Prettier, path aliases
- [ ] Definir estructura de carpetas (`components/`, `hooks/`, `services/`, `pages/`)
- [ ] Configurar cliente HTTP (axios o fetch wrapper) apuntando a LocalStack
- [ ] Routing básico (react-router-dom)

### Panel de Estado
- [ ] Implementar `StatusPanel` component
  - [ ] Estado de Kinesis input/output streams (métricas básicas)
  - [ ] Estado de la Lambda (última ejecución, errores)
  - [ ] Estado de DynamoDB (items count)
  - [ ] Estado de Redis (memoria usada, keys)
  - [ ] Estado de PostgreSQL (conexión activa)
- [ ] Polling periódico o WebSocket para refrescar estado
- [ ] Indicadores visuales: verde/amarillo/rojo por recurso

---

## 🔵 Sprint 6 — Dashboard: Gestión de Streams & Logs

> Objetivo: capacidad de leer/escribir en streams y ver logs de Lambda.

### Lectura de Streams
- [ ] Implementar `StreamReader` component
  - [ ] Selector de stream (input / output)
  - [ ] Vista de mensajes en tiempo real (polling o SSE)
  - [ ] Formateo JSON con syntax highlighting
  - [ ] Filtrado y búsqueda de mensajes

### Escritura en Streams
- [ ] Implementar `StreamWriter` component
  - [ ] Editor JSON para componer el mensaje
  - [ ] Validación del schema `InputMessage` en cliente
  - [ ] Botón de envío con feedback de confirmación/error
  - [ ] Templates predefinidos para los dos casos (registration / baseline)

### Visor de Logs (CloudWatch)
- [ ] Implementar `LogViewer` component
  - [ ] Selector de Lambda / log group
  - [ ] Listado de log streams
  - [ ] Visualización de eventos con timestamp, nivel y mensaje
  - [ ] Auto-scroll y pausa
  - [ ] Filtrado por nivel (INFO, WARN, ERROR)

---

## 🟤 Sprint 7 — Dashboard: Simulador de Carga & Visualización Gráfica

> Objetivo: herramienta de simulación de carga con representación visual en tiempo real.

### Simulador de Carga
- [ ] Implementar `LoadSimulator` component
  - [ ] Configuración de la simulación:
    - Número de mensajes totales
    - Tasa de mensajes/segundo
    - Ratio registration_id vs baseline_id
    - Rango de `node_id` y `dispatch_unit`
  - [ ] Generador de mensajes aleatorios conformes al schema
  - [ ] Control: Start / Pause / Stop
  - [ ] Contador de mensajes enviados / errores en tiempo real

### Visualización Gráfica
- [ ] Integrar librería de gráficas (Recharts / Chart.js)
- [ ] Implementar `SimulationDashboard` con gráficas en tiempo real:
  - [ ] **Throughput:** mensajes/segundo en el input-stream
  - [ ] **Latencia Lambda:** tiempo de procesamiento por lote
  - [ ] **Distribución de registros:** metered vs baseline
  - [ ] **Cache hit rate:** hits vs misses en Redis
  - [ ] **Output stream:** mensajes publicados en el tiempo
- [ ] Exportar resultados de simulación a JSON/CSV

---

## ⚫ Sprint 8 — Integración Final, Hardening & Documentación

> Objetivo: el sistema es estable, observable y listo para presentar o desplegar.

### Integración End-to-End
- [x] Test completo del flujo: `scripts/e2e-smoke.sh` + `scripts/e2e-batch.sh`
- [x] Ajuste de configuración Terraform para producción (variable `localstack = false`)
- [x] Verificar TTL en DynamoDB con datos reales (TTL = dttm_utc + 24h)

### Resiliencia & Observabilidad
- [x] Implementar Dead Letter Queue (DLQ) para mensajes fallidos en Lambda
- [x] Alertas en CloudWatch: errores > 10, duración p99 > 10s, DLQ profundidad > 0
- [x] Reintentos con backoff exponencial en `ValueClient` (filtrado por tipo de error)
- [x] Bisect batch on failure + máximo 3 reintentos en el trigger Lambda

### Seguridad
- [x] IAM roles mínimos para la Lambda (principio de menor privilegio)
- [x] Secrets gestionados via AWS Secrets Manager (`aws-local/db-credentials`)
- [x] Variables de entorno sensibles fuera del código
- [x] HTTPS en Value Backend y Dashboard en producción (pendiente de entorno real)

### Testing Final
- [x] Scripts E2E automatizados (`e2e-smoke.sh`, `e2e-batch.sh`)
- [x] Tests de carga Scala (`LoadSpec.scala` — 9 tests de dominio/batch/TTL)
- [x] Cobertura de tests: umbral 80% configurado con sbt-scoverage
- [x] Dashboard: 14/14 tests passing; value-backend: 6/6 tests passing

### Documentación
- [x] `README.md` principal con diagramas y badges
- [x] `pipeline/README.md` — arquitectura, env vars, build, tests
- [x] `dashboard/README.md` — setup, scripts, páginas, variables
- [x] `infra/README.md` — recursos, uso local y producción
- [x] `CONTRIBUTING.md` con convenciones de código y flujo de PRs
- [x] `.scalafmt.conf` + `eslint.config.js` para calidad de código

---

## 📊 Resumen de Sprints

| Sprint | Nombre | Componente | Dependencias |
|---|---|---|---|
| 0 | Fundamentos & Entorno | Transversal | — |
| 1 | Value Backend | `value-backend/` | Sprint 0 |
| 2 | Pipeline: Modelos & Config | `pipeline/` | Sprint 0 |
| 3 | Pipeline: Integraciones AWS | `pipeline/` | Sprint 1, 2 |
| 4 | Pipeline: Lógica & Lambda | `pipeline/` | Sprint 3 |
| 5 | Dashboard: Estado | `dashboard/` | Sprint 0 |
| 6 | Dashboard: Streams & Logs | `dashboard/` | Sprint 4, 5 |
| 7 | Dashboard: Simulador | `dashboard/` | Sprint 6 |
| 8 | Integración & Hardening | Transversal | Sprint 4, 7 |
