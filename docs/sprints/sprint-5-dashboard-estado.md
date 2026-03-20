# Sprint 5 — Dashboard: Estructura & Panel de Estado

## Objetivo

Crear el proyecto React/TypeScript con la arquitectura base y el primer módulo funcional: el panel de estado de los recursos AWS.

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 0 (infraestructura local levantada)

---

## 1. Inicialización del Proyecto

```bash
cd dashboard
npm create vite@latest . -- --template react-ts
npm install
```

### Dependencias adicionales

```bash
# Routing
npm install react-router-dom

# HTTP
npm install axios

# UI
npm install @radix-ui/react-icons lucide-react
npm install tailwindcss @tailwindcss/vite
# O alternativamente: npm install @mui/material @emotion/react @emotion/styled

# Estado (ligero)
npm install zustand

# Tests
npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
```

---

## 2. Estructura de Carpetas

```
dashboard/src/
├── api/
│   ├── client.ts           # axios instance
│   ├── kinesis.ts
│   ├── lambda.ts
│   ├── dynamodb.ts
│   ├── redis.ts
│   └── postgres.ts
├── components/
│   ├── layout/
│   │   ├── AppLayout.tsx
│   │   ├── Sidebar.tsx
│   │   └── Header.tsx
│   ├── status/
│   │   ├── StatusPanel.tsx
│   │   ├── ResourceCard.tsx
│   │   └── StatusBadge.tsx
│   └── common/
│       ├── LoadingSpinner.tsx
│       └── ErrorBoundary.tsx
├── hooks/
│   ├── useResourceStatus.ts
│   └── usePolling.ts
├── pages/
│   ├── StatusPage.tsx
│   ├── StreamsPage.tsx     # Sprint 6
│   ├── LogsPage.tsx        # Sprint 6
│   └── SimulatorPage.tsx   # Sprint 7
├── store/
│   └── resourceStore.ts
├── types/
│   └── resources.ts
├── App.tsx
└── main.tsx
```

---

## 3. Configuración Base

### `vite.config.ts`

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  },
  server: {
    port: 5173,
    proxy: {
      // Proxy a LocalStack para evitar CORS en desarrollo
      '/localstack': {
        target: 'http://localhost:4566',
        rewrite: (p) => p.replace(/^\/localstack/, ''),
        changeOrigin: true
      }
    }
  }
})
```

### `src/api/client.ts`

```typescript
import axios from 'axios'

export const localstackClient = axios.create({
  baseURL: 'http://localhost:4566',
  headers: {
    'x-amz-content-sha256': 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    'Authorization': 'AWS4-HMAC-SHA256 Credential=test/20260101/us-east-1/kinesis/aws4_request, SignedHeaders=host, Signature=test'
  }
})

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
})
```

---

## 4. Tipos

### `src/types/resources.ts`

```typescript
export type ResourceStatus = 'healthy' | 'degraded' | 'error' | 'unknown'

export interface ResourceInfo {
  name:        string
  type:        'kinesis' | 'lambda' | 'dynamodb' | 'redis' | 'postgres'
  status:      ResourceStatus
  details:     Record<string, string | number>
  lastChecked: Date
}

export interface KinesisStreamInfo extends ResourceInfo {
  type: 'kinesis'
  details: {
    streamName:    string
    shardCount:    number
    retentionHours: number
    sequenceNumber?: string
  }
}

export interface LambdaInfo extends ResourceInfo {
  type: 'lambda'
  details: {
    functionName:  string
    runtime:       string
    lastModified:  string
    lastInvocation?: string
    errorRate?:    number
  }
}

export interface DynamoInfo extends ResourceInfo {
  type: 'dynamodb'
  details: {
    tableName:    string
    itemCount:    number
    status:       string
  }
}

export interface RedisInfo extends ResourceInfo {
  type: 'redis'
  details: {
    usedMemory:   string
    connectedClients: number
    totalKeys:    number
  }
}

export interface PostgresInfo extends ResourceInfo {
  type: 'postgres'
  details: {
    database:         string
    activeConnections: number
    intervalCount:    number
  }
}
```

---

## 5. Hooks

### `src/hooks/usePolling.ts`

```typescript
import { useEffect, useRef } from 'react'

export function usePolling(callback: () => void, intervalMs: number, enabled = true) {
  const savedCallback = useRef(callback)

  useEffect(() => { savedCallback.current = callback }, [callback])

  useEffect(() => {
    if (!enabled) return
    savedCallback.current()
    const id = setInterval(() => savedCallback.current(), intervalMs)
    return () => clearInterval(id)
  }, [intervalMs, enabled])
}
```

### `src/hooks/useResourceStatus.ts`

```typescript
import { useState, useCallback } from 'react'
import { usePolling } from './usePolling'
import type { ResourceInfo } from '@/types/resources'
import { fetchAllResourceStatuses } from '@/api/status'

export function useResourceStatus(intervalMs = 10_000) {
  const [resources, setResources] = useState<ResourceInfo[]>([])
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchAllResourceStatuses()
      setResources(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error desconocido')
    } finally {
      setLoading(false)
    }
  }, [])

  usePolling(refresh, intervalMs)

  return { resources, loading, error, refresh }
}
```

---

## 6. Componentes del Panel de Estado

### `src/components/status/StatusBadge.tsx`

```tsx
import type { ResourceStatus } from '@/types/resources'

const styles: Record<ResourceStatus, string> = {
  healthy:  'bg-green-100 text-green-800',
  degraded: 'bg-yellow-100 text-yellow-800',
  error:    'bg-red-100 text-red-800',
  unknown:  'bg-gray-100 text-gray-600'
}

const labels: Record<ResourceStatus, string> = {
  healthy: 'Operativo', degraded: 'Degradado', error: 'Error', unknown: 'Desconocido'
}

interface Props { status: ResourceStatus }

export function StatusBadge({ status }: Props) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${styles[status]}`}>
      <span className="w-1.5 h-1.5 rounded-full mr-1.5 bg-current opacity-70" />
      {labels[status]}
    </span>
  )
}
```

### `src/components/status/ResourceCard.tsx`

```tsx
import type { ResourceInfo } from '@/types/resources'
import { StatusBadge } from './StatusBadge'

interface Props {
  resource: ResourceInfo
  onClick?: () => void
}

export function ResourceCard({ resource, onClick }: Props) {
  return (
    <div
      className="bg-white rounded-lg border border-gray-200 p-4 cursor-pointer hover:shadow-md transition-shadow"
      onClick={onClick}
    >
      <div className="flex justify-between items-start mb-3">
        <div>
          <h3 className="font-semibold text-gray-900">{resource.name}</h3>
          <p className="text-xs text-gray-500 uppercase tracking-wide">{resource.type}</p>
        </div>
        <StatusBadge status={resource.status} />
      </div>

      <dl className="space-y-1">
        {Object.entries(resource.details).map(([k, v]) => (
          <div key={k} className="flex justify-between text-sm">
            <dt className="text-gray-500">{k}</dt>
            <dd className="font-mono text-gray-800">{String(v)}</dd>
          </div>
        ))}
      </dl>

      <p className="text-xs text-gray-400 mt-3">
        Actualizado: {resource.lastChecked.toLocaleTimeString()}
      </p>
    </div>
  )
}
```

### `src/components/status/StatusPanel.tsx`

```tsx
import { useResourceStatus } from '@/hooks/useResourceStatus'
import { ResourceCard } from './ResourceCard'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'

export function StatusPanel() {
  const { resources, loading, error, refresh } = useResourceStatus(10_000)

  if (loading) return <LoadingSpinner />

  if (error) return (
    <div className="p-4 bg-red-50 rounded-lg">
      <p className="text-red-700">{error}</p>
      <button onClick={refresh} className="mt-2 text-sm text-red-600 underline">
        Reintentar
      </button>
    </div>
  )

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-xl font-bold text-gray-900">Estado de Recursos</h2>
        <button
          onClick={refresh}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          ↻ Actualizar
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {resources.map(r => (
          <ResourceCard key={`${r.type}-${r.name}`} resource={r} />
        ))}
      </div>
    </div>
  )
}
```

---

## 7. Routing y Layout

### `src/App.tsx`

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { StatusPage } from '@/pages/StatusPage'

export default function App() {
  return (
    <BrowserRouter>
      <AppLayout>
        <Routes>
          <Route path="/"         element={<Navigate to="/status" replace />} />
          <Route path="/status"   element={<StatusPage />} />
          {/* Sprints 6 y 7: */}
          {/* <Route path="/streams"  element={<StreamsPage />} /> */}
          {/* <Route path="/logs"     element={<LogsPage />} /> */}
          {/* <Route path="/simulate" element={<SimulatorPage />} /> */}
        </Routes>
      </AppLayout>
    </BrowserRouter>
  )
}
```

---

## 8. Tests

### Configurar Vitest

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true
  },
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') }
  }
})
```

```typescript
// src/test/setup.ts
import '@testing-library/jest-dom'
```

### Test del StatusBadge

```tsx
// src/components/status/__tests__/StatusBadge.test.tsx
import { render, screen } from '@testing-library/react'
import { StatusBadge } from '../StatusBadge'

describe('StatusBadge', () => {
  it('muestra "Operativo" para status healthy', () => {
    render(<StatusBadge status="healthy" />)
    expect(screen.getByText('Operativo')).toBeInTheDocument()
  })

  it('muestra "Error" para status error', () => {
    render(<StatusBadge status="error" />)
    expect(screen.getByText('Error')).toBeInTheDocument()
  })
})
```

---

## Criterios de Aceptación

- [ ] `npm run dev` arranca el dashboard en `http://localhost:5173`
- [ ] El panel de estado muestra cards para los 5 recursos (Kinesis x2, Lambda, DynamoDB, Redis)
- [ ] El estado se refresca automáticamente cada 10 segundos
- [ ] El botón "Actualizar" fuerza un refresco manual
- [ ] Los indicadores de color reflejan el estado real de LocalStack
- [ ] `npm test` pasa los tests de componentes
- [ ] `npm run build` genera el bundle sin errores de TypeScript
