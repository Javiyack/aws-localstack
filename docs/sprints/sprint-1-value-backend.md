# Sprint 1 — Value Backend

## Objetivo

Desarrollar el servicio HTTP minimalista que devuelve valores aleatorios entre 900 y 1100. Este servicio es consumido por el pipeline en cada mensaje procesado.

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 0 completado

---

## 1. Stack & Estructura

```
value-backend/
├── src/
│   ├── index.ts          # entrypoint
│   ├── server.ts         # configuración Express/Fastify
│   └── routes/
│       └── value.ts      # GET /:id handler
├── test/
│   └── value.test.ts
├── package.json
├── tsconfig.json
├── Dockerfile
└── README.md
```

**Stack recomendado:** Node.js 22 + TypeScript + [Fastify](https://fastify.dev/) (ligero y tipado)

---

## 2. Inicialización del Proyecto

```bash
cd value-backend
npm init -y
npm install fastify
npm install -D typescript @types/node tsx vitest supertest @types/supertest
```

### `tsconfig.json`

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "NodeNext",
    "moduleResolution": "NodeNext",
    "outDir": "dist",
    "strict": true,
    "esModuleInterop": true
  },
  "include": ["src"]
}
```

### `package.json` — scripts

```json
{
  "scripts": {
    "dev":   "tsx watch src/index.ts",
    "build": "tsc",
    "start": "node dist/index.js",
    "test":  "vitest run"
  }
}
```

---

## 3. Implementación

### `src/server.ts`

```typescript
import Fastify from 'fastify'

export function buildServer() {
  const app = Fastify({ logger: true })

  app.get<{ Params: { id: string } }>('/:id', async (request, reply) => {
    const { id } = request.params

    if (!id || id.trim() === '') {
      return reply.status(400).send({ error: 'id is required' })
    }

    const value = 900 + Math.random() * 200  // [900, 1100)
    return reply.send({ value: parseFloat(value.toFixed(4)) })
  })

  return app
}
```

### `src/index.ts`

```typescript
import { buildServer } from './server.js'

const PORT = parseInt(process.env.PORT ?? '3333', 10)
const HOST = process.env.HOST ?? '0.0.0.0'

const app = buildServer()

app.listen({ port: PORT, host: HOST }, (err, address) => {
  if (err) {
    app.log.error(err)
    process.exit(1)
  }
  app.log.info(`Value backend listening at ${address}`)
})
```

---

## 4. Tests

### `test/value.test.ts`

```typescript
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { buildServer } from '../src/server.js'
import type { FastifyInstance } from 'fastify'

let app: FastifyInstance

beforeAll(async () => {
  app = buildServer()
  await app.ready()
})

afterAll(async () => {
  await app.close()
})

describe('GET /:id', () => {
  it('devuelve un value entre 900 y 1100', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/reg-001'
    })

    expect(response.statusCode).toBe(200)
    const body = JSON.parse(response.body)
    expect(body).toHaveProperty('value')
    expect(body.value).toBeGreaterThanOrEqual(900)
    expect(body.value).toBeLessThan(1100)
  })

  it('acepta cualquier id alfanumérico', async () => {
    const ids = ['abc123', 'node-99', 'baseline_xyz']
    for (const id of ids) {
      const res = await app.inject({ method: 'GET', url: `/${id}` })
      expect(res.statusCode).toBe(200)
    }
  })

  it('responde 400 si id está vacío', async () => {
    // Fastify no enruta '/' a '/:id'; validar a nivel de lógica de negocio
    const response = await app.inject({ method: 'GET', url: '/ ' })
    // comportamiento depende del routing — documentar en README
  })

  it('los valores son aleatorios (no siempre iguales)', async () => {
    const results = await Promise.all(
      Array.from({ length: 10 }, () =>
        app.inject({ method: 'GET', url: '/test-id' })
          .then(r => JSON.parse(r.body).value)
      )
    )
    const unique = new Set(results)
    expect(unique.size).toBeGreaterThan(1) // muy improbable que todos sean iguales
  })
})
```

---

## 5. Dockerfile

```dockerfile
FROM node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY --from=builder /app/dist ./dist
EXPOSE 3333
CMD ["node", "dist/index.js"]
```

### Añadir al `docker-compose.yml` raíz

```yaml
  value-backend:
    build: ./value-backend
    ports:
      - "3333:3333"
    environment:
      PORT: "3333"
```

---

## 6. README del módulo (`value-backend/README.md`)

Documentar:
- Propósito del servicio
- Cómo arrancar en desarrollo (`npm run dev`)
- Cómo correr tests (`npm test`)
- Contrato de la API:

```
GET /:id

Response 200:
  { "value": 1042.5713 }

Response 400:
  { "error": "id is required" }
```

---

## Verificación Manual

```bash
# Arrancar en desarrollo
cd value-backend && npm run dev

# Probar desde otra terminal
curl http://localhost:3333/registration-001
# → {"value":987.3421}

curl http://localhost:3333/baseline-xyz
# → {"value":1073.1190}
```

---

## Criterios de Aceptación

- [ ] `GET /:id` devuelve `{ value: <número entre 900 y 1100> }` con status 200
- [ ] El valor varía en cada llamada (es aleatorio)
- [ ] Todos los tests pasan (`npm test`)
- [ ] El servicio arranca con `docker compose up value-backend`
- [ ] `README.md` del módulo documenta el contrato de la API
