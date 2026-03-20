# Value Backend

Servicio HTTP minimalista que devuelve un número real aleatorio entre **900 y 1100** para cualquier `:id` solicitado.

## Stack

- Node.js 22
- TypeScript
- Fastify 5

## Desarrollo

```bash
npm install
npm run dev
# Escucha en http://localhost:3333
```

## Tests

```bash
npm test
```

## API

### `GET /health`

```json
{ "status": "ok" }
```

### `GET /:id`

Devuelve un valor aleatorio asociado al identificador (el ID no altera la lógica; es un mock).

**Respuesta 200:**
```json
{ "value": 1042.5713 }
```

El valor es un `Double` en el rango `[900, 1100)` con hasta 4 decimales.

## Docker

```bash
# Build
docker build -t value-backend .

# Run
docker run -p 3333:3333 value-backend

# O con docker compose desde la raíz del proyecto:
docker compose up value-backend
```
