# 🚀 Ecosistema de Procesamiento de Datos AWS

Pipeline de procesamiento de datos de alto rendimiento construido sobre **AWS**, complementado con un servicio de valores y una interfaz de gestión y monitoreo en tiempo real.

![Scala](https://img.shields.io/badge/Scala-3.x-DC322F?logo=scala&logoColor=white)
![ZIO](https://img.shields.io/badge/ZIO-2.x-E84393?logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js-22-339933?logo=node.js&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript&logoColor=white)
![Terraform](https://img.shields.io/badge/Terraform-IaC-7B42BC?logo=terraform&logoColor=white)
![LocalStack](https://img.shields.io/badge/LocalStack-local%20AWS-1A9C3E?logoColor=white)

---

## 📋 Tabla de Contenidos

1. [Arquitectura General](#arquitectura-general)
2. [Pipeline de Datos (Backend)](#1-pipeline-de-datos-backend)
3. [Value Backend](#2-value-backend)
4. [Pipeline Management & Dashboard (Frontend)](#3-pipeline-management--dashboard-frontend)
5. [Estructura del Proyecto](#estructura-del-proyecto)
6. [Inicio Rápido](#inicio-rápido)

---

## Arquitectura General

```
Kinesis (Input Stream)
        │
        ▼ lotes de 500 mensajes
  AWS Lambda (Scala/ZIO)
        │
        ├──► DynamoDB  (audit, TTL = dttm_utc + 24h)
        │
        ├──► Value Backend (GET :3333/:id)
        │         │
        │         ▼
        ├──► Redis  (upsert por dispatch_unit)
        │
        ├──► PostgreSQL  (persistencia final)
        │
        └──► Kinesis (Output Stream)  ──► Dashboard (React)
```

---

## 🏗️ 1. Pipeline de Datos (Backend)

Arquitectura reactiva y funcional para el procesamiento de flujos de datos a gran escala utilizando el ecosistema de **Scala**.

### Stack Tecnológico

| Categoría | Tecnología |
|---|---|
| Lenguaje | `Scala v3.x` con enfoque funcional |
| Efectos & Concurrencia | `ZIO v2.x` — gestión de fibras y recursos |
| Infraestructura como Código | `Terraform` + `LocalStack` (emulación local) |
| Ingesta | `AWS Kinesis` |
| Procesamiento | `AWS Lambda` (serverless, lotes de 500) |
| Observabilidad | `AWS CloudWatch` |
| NoSQL / Audit | `AWS DynamoDB` |
| Relacional | `PostgreSQL` vía `Slick` |
| Caché | `Redis` vía `ZIO Redis` |
| Clientes Redis avanzados | `Redisson` / `Lettuce` (clusters y locks distribuidos) |
| HTTP Client | `sttp3` (asíncrono y tipado) |
| JSON | `Circe` |
| DynamoDB Type-safe | `Scanamo` |

### Flujo de Procesamiento

#### 1. Consumo en Lotes
AWS Lambda consume registros desde el stream de entrada de **Kinesis** en lotes de **500 mensajes**.

#### 2. Estructura del Mensaje de Entrada (Payload)

```json
{
  "node_id":         "String   (Obligatorio)",
  "dttm_utc":        "Instant  (Obligatorio)",
  "registration_id": "String   (Opcional — mutuamente excluyente con baseline_id)",
  "baseline_id":     "String   (Opcional — mutuamente excluyente con registration_id)"
}
```

> **Nota:** cada mensaje incluye `registration_id` **o** `baseline_id`, nunca ambos.

#### 3. Auditoría
Cada mensaje recibido se persiste en **DynamoDB** con marca de tiempo y `TTL = dttm_utc + 24h`.

#### 4. Integración con Value Backend
Por cada mensaje procesado se realiza una llamada HTTP al servicio de valores local:

| Campo presente | Endpoint |
|---|---|
| `registration_id` | `GET http://localhost:3333/:registration_id` |
| `baseline_id` | `GET http://localhost:3333/:baseline_id` |

#### 5. Generación del Mensaje de Salida (`performance_interval`)

```json
{
  "dispatch_unit":  "String  (Obligatorio)",
  "node_id":        "String  (Obligatorio)",
  "dttm_utc":       "Instant (Obligatorio)",
  "metered_value":  "Double  (Opcional)",
  "baseline_value": "Double  (Opcional)"
}
```

| Caso | Campo poblado |
|---|---|
| Se usó `registration_id` | `metered_value` ← respuesta HTTP; `baseline_value` indefinido |
| Se usó `baseline_id` | `baseline_value` ← respuesta HTTP; `metered_value` indefinido |

#### 6. Caché en Redis
Se realiza un **upsert** de `performance_interval` en Redis por `dispatch_unit`, actualizando solo los campos definidos y devolviendo el objeto completo. Esto permite acumular `metered_value` y `baseline_value` de lotes distintos para el mismo `dispatch_unit`.

#### 7. Persistencia
Tras actualizar Redis, el `performance_interval` resultante se almacena en **PostgreSQL**.

#### 8. Salida
El `performance_interval` final se escribe al **stream de salida de Kinesis**.

---

## 🖥️ 2. Value Backend

Servicio web minimalista que devuelve un número real aleatorio entre **900 y 1100**.

**Stack:** NodeJS / cualquier servidor HTTP ligero.

```
GET http://localhost:3333/:id
```

**Respuesta:**
```json
{ "value": 1042.57 }
```

---

## 🖥️ 3. Pipeline Management & Dashboard (Frontend)

Interfaz administrativa para el control total y la observabilidad del pipeline en tiempo real.

### Stack Tecnológico

*   **Runtime:** `Node.js v22` (LTS)
*   **Lenguaje:** `TypeScript`
*   **Librería UI:** `React` (arquitectura basada en componentes)

### Funcionalidades

| Módulo | Descripción |
|---|---|
| **Panel de Estado** | Visualización del estado operativo de cada recurso AWS |
| **Lectura de Streams** | Consumo en tiempo real de mensajes desde Kinesis |
| **Escritura en Streams** | Publicación manual de mensajes para pruebas |
| **Visor de Logs** | Logs de CloudWatch de las AWS Lambda en tiempo real |
| **Simulador de Carga** | Generación automática de mensajes de entrada sintéticos |
| **Visualización Gráfica** | Gráficas dinámicas de actividad durante la simulación |

---

## 📁 Estructura del Proyecto

```
aws-localstack/
├── pipeline/          # Backend Scala/ZIO — AWS Lambda + procesamiento
├── value-backend/     # Servicio HTTP de valores aleatorios
├── dashboard/         # Frontend React/TypeScript
├── infra/             # Terraform + LocalStack (IaC)
└── README.md
```

---

## 🚀 Inicio Rápido

### Prerrequisitos

*   `Java 21+` / `Scala 3.x` / `sbt`
*   `Node.js v22`
*   `Terraform`
*   `Docker` (para LocalStack)

### Levantar el entorno local

```bash
# 1. Iniciar LocalStack (emula AWS localmente)
docker run --rm -d -p 4566:4566 localstack/localstack

# 2. Provisionar infraestructura con Terraform
cd infra && terraform init && terraform apply

# 3. Iniciar el Value Backend
cd value-backend && npm install && npm start

# 4. Iniciar el Dashboard
cd dashboard && npm install && npm run dev

# 5. Compilar y desplegar la Lambda
cd pipeline && sbt assembly
```
