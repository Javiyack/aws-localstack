# 🚀 Proyecto: Ecosistema de Procesamiento de Datos AWS

Este proyecto consiste en un pipeline de procesamiento de datos de alto rendimiento construido sobre **AWS**, complementado con una interfaz de gestión y monitoreo en tiempo real.

---

## 🏗️ 1. Pipeline de Datos (Backend)

Arquitectura reactiva y funcional para el procesamiento de flujos de datos a gran escala utilizando el ecosistema de **Scala**.

### **Stack Tecnológico**
*   **Lenguaje:** `Scala v3.x` (Latest) con enfoque funcional.
*   **Efectos & Concurrencia:** `ZIO v2.x` para gestión de fibras y recursos.
*   **Infraestructura como Código:** `Terraform` para el despliegue y `LocalStack` para emulación local.
*   **Servicios AWS:** 
    *   **Kinesis:** Ingesta de flujos de datos.
    *   **AWS Lambda:** Procesamiento serverless en lotes.
    *   **CloudWatch:** Almacenamiento de logs y métricas.
    *   **DynamoDB:** Persistencia NoSQL de alto rendimiento.
*   **Almacenamiento & Caché:** `PostgreSQL` (Relacional) y `Redis` (Caché de baja latencia).
*   **Librerías Clave:**
    *   `Slick`: Acceso a base de datos relacional.
    *   `Scanamo`: Interfaz Type-safe para DynamoDB.
    *   `ZIO Redis`: Cliente puramente funcional y no bloqueante para Redis (nativo de ZIO).
    *   `Redisson` o `Lettuce`: Clientes robustos de Java para gestión de clusters y locks distribuidos.
    *   `sttp3`: Cliente HTTP asíncrono y tipado.
    *   `Circe`: Serialización/Deserialización de JSON.

### **Requisitos Funcionales**
*   **Consumo en Lotes:** AWS Lambda consume registros desde Kinesis en lotes de **500 mensajes**.
*   **Estructura del Mensaje (Payload):**
    ```json
    {
      "node_id": "String (Obligatorio)",
      "dttm_utc": "Instant (Obligatorio)",
      "registration_id": "String (Opcional)",
      "baseline_id": "String (Opcional)"
    }
    ```
    Incluye registration_id o baseline_id pero no ambos.
*   **audit** Casa mensaje se guarda en DynamoDB con marca de tiempo y ttl=dttm_utc + 24h
*   **Integración Externa:** Por cada mensaje procesado, se realiza una llamada `HTTP GET` a un servicio externo:  
    - `http://localhost:3333/:registration_id` (utilizando el `registration_id``del mensaje, si esta definido).
    - `http://localhost:3333/:baseline_id` (utilizando el `baseline_id`del mensaje, si esta definido).
*   **Procesamiento:** De la respuesta se obtiene un valor y se genera un nuevo mesaje `performance_interval` como sigue:
    ```json
    {
      "dispatch_unit": "String (Obligatorio)",
      "node_id": "String (Obligatorio)",
      "dttm_utc": "Instant (Obligatorio)",
      "metered_value": "Double (Opcional)",
      "baseline_value": "Double (Opcional)",
      "baseline_id": "String (Opcional)"
    }
    ```
    Si se utilizo registration_id metered_value toma el valor de la respuesta y baseline_value queda indefinido.
    Si se utilizo baseline_id baseline_value toma el valor de la respuesta y metered_value queda indefinido.
*   **Cache:** Se hace upsert de `performance_interval`en Redis por `dispatch_unit`actualizandi solo los campos que esten definidos y recuperando el objeto actualizado en la respuesta. Asi, si para un `dispatch_unit`en un lote anterior se guardo el `metered_value` y en tro posterior se guarda el `baseline_value`, la reapuesta de Redis contendra ambos `metered_value` y `baseline_value`
*   **Persistencia:** Tras actualizar en Redis Almacenamiento de resultados en PostgreSQL
*   **Salida:** Se escribe el `performance_interval` al stream de salida


---

## 🖥️ 2. Value backend

Una simple aplixacion web que devuelve un numero real aleatorio `value`entre 900 y 1100
- `GET / http://localhost:3333/:id`

---

## 🖥️ 3. Pipeline Management & Dashboard (Frontend)

Interfaz administrativa para el control total y la observabilidad de los recursos del pipeline.

### **Stack Tecnológico**
*   **Runtime:** `NodeJS v22` (LTS).
*   **Lenguaje:** `TypeScript`.
*   **Librería UI:** `React` (con arquitectura basada en componentes).

### **Requisitos Funcionales**
*   **Panel de Estado:** Visualización informativa de los distintos recursos de AWS y su estado operativo actual.
*   **Gestión de Streams:**
    *   Lectura en tiempo real de los datos que fluyen por los streams.
    *   Escritura manual de mensajes para pruebas de flujo.
*   **Observabilidad:** Visor de logs de CloudWatch para monitorizar el comportamiento de las Lambdas.
*   **Simulador de Carga:** 
    *   Generación automática de carga de trabajo mediante mensajes de entrada sintéticos.
    *   **Visualización Gráfica:** Representación dinámica mediante gráficas de la actividad de los distintos recursos durante la simulación.
