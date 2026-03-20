# Sprint 7 — Dashboard: Simulador de Carga & Visualización Gráfica

## Objetivo

Implementar el simulador de carga de trabajo con generación automática de mensajes y la visualización gráfica en tiempo real de la actividad del pipeline.

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 6 completado

---

## 1. Dependencias

```bash
npm install recharts
npm install -D @types/recharts   # si es necesario
```

---

## 2. Estructura de Archivos

```
dashboard/src/
├── pages/
│   └── SimulatorPage.tsx
├── components/
│   └── simulator/
│       ├── SimulatorControls.tsx
│       ├── SimulatorStats.tsx
│       ├── charts/
│       │   ├── ThroughputChart.tsx
│       │   ├── DistributionChart.tsx
│       │   ├── LatencyChart.tsx
│       │   └── OutputRateChart.tsx
│       └── SimulationDashboard.tsx
├── hooks/
│   └── useSimulator.ts
├── services/
│   └── messageGenerator.ts
└── types/
    └── simulation.ts
```

---

## 3. Tipos de Simulación

### `src/types/simulation.ts`

```typescript
export interface SimulationConfig {
  totalMessages:       number    // total de mensajes a enviar
  ratePerSecond:       number    // mensajes/segundo
  registrationRatio:  number    // 0-1 (proporción de registration vs baseline)
  nodeIdCount:        number    // variedad de node_id (1-100)
  dispatchUnitCount:  number    // variedad de dispatch_unit (1-50)
}

export interface SimulationStats {
  sent:          number
  errors:        number
  elapsedMs:     number
  rateActual:    number    // mensajes/s reales
  status:        'idle' | 'running' | 'paused' | 'completed' | 'error'
}

export interface DataPoint {
  timestamp: number       // ms epoch
  value:     number
  label:     string
}

export interface SimulationMetrics {
  throughput:    DataPoint[]   // msgs/s enviados
  errors:        DataPoint[]   // errores acumulados
  distribution:  { name: string; value: number }[]  // registration vs baseline
}
```

---

## 4. Generador de Mensajes

### `src/services/messageGenerator.ts`

```typescript
import type { SimulationConfig } from '@/types/simulation'
import type { InputMessage } from '@/types/messages'

function randomId(prefix: string, count: number): string {
  const n = Math.floor(Math.random() * count) + 1
  return `${prefix}-${String(n).padStart(3, '0')}`
}

export function generateMessage(config: SimulationConfig): InputMessage {
  const isRegistration = Math.random() < config.registrationRatio
  const nodeId         = randomId('node',     config.nodeIdCount)
  const dttmUtc        = new Date().toISOString()

  return isRegistration
    ? {
        nodeId,
        dttmUtc,
        registrationId: randomId('reg', config.dispatchUnitCount),
        baselineId:     undefined
      }
    : {
        nodeId,
        dttmUtc,
        registrationId: undefined,
        baselineId:     randomId('base', config.dispatchUnitCount)
      }
}
```

---

## 5. Hook del Simulador

### `src/hooks/useSimulator.ts`

```typescript
import { useState, useRef, useCallback } from 'react'
import { putRecord } from '@/api/kinesis'
import { generateMessage } from '@/services/messageGenerator'
import type { SimulationConfig, SimulationStats, SimulationMetrics, DataPoint } from '@/types/simulation'

const DEFAULT_CONFIG: SimulationConfig = {
  totalMessages:      1000,
  ratePerSecond:      10,
  registrationRatio:  0.5,
  nodeIdCount:        20,
  dispatchUnitCount:  10
}

export function useSimulator() {
  const [config, setConfig]   = useState<SimulationConfig>(DEFAULT_CONFIG)
  const [stats, setStats]     = useState<SimulationStats>({
    sent: 0, errors: 0, elapsedMs: 0, rateActual: 0, status: 'idle'
  })
  const [metrics, setMetrics] = useState<SimulationMetrics>({
    throughput: [], errors: [], distribution: [
      { name: 'Registration', value: 0 },
      { name: 'Baseline',     value: 0 }
    ]
  })

  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const startTimeRef = useRef<number>(0)
  const sentRef      = useRef(0)
  const errorsRef    = useRef(0)

  const addDataPoint = (type: 'throughput' | 'errors', value: number) => {
    const point: DataPoint = { timestamp: Date.now(), value, label: new Date().toLocaleTimeString() }
    setMetrics(prev => ({
      ...prev,
      [type]: [...prev[type].slice(-60), point]  // max 60 puntos
    }))
  }

  const start = useCallback(async () => {
    sentRef.current   = 0
    errorsRef.current = 0
    startTimeRef.current = Date.now()

    setStats({ sent: 0, errors: 0, elapsedMs: 0, rateActual: 0, status: 'running' })
    setMetrics({
      throughput: [], errors: [],
      distribution: [{ name: 'Registration', value: 0 }, { name: 'Baseline', value: 0 }]
    })

    const intervalMs  = 1000 / config.ratePerSecond
    const batchWindow = 100  // ms entre lotes de envío

    intervalRef.current = setInterval(async () => {
      if (sentRef.current >= config.totalMessages) {
        stop()
        return
      }

      // Enviar mensajes en el período actual
      const msgsThisTick = Math.min(
        Math.ceil(config.ratePerSecond * batchWindow / 1000),
        config.totalMessages - sentRef.current
      )

      await Promise.allSettled(
        Array.from({ length: msgsThisTick }, async () => {
          try {
            const msg = generateMessage(config)
            await putRecord('input-stream', msg)
            sentRef.current++

            setMetrics(prev => ({
              ...prev,
              distribution: prev.distribution.map(d =>
                (d.name === 'Registration' && msg.registrationId) ||
                (d.name === 'Baseline'     && msg.baselineId)
                  ? { ...d, value: d.value + 1 }
                  : d
              )
            }))
          } catch {
            errorsRef.current++
          }
        })
      )

      const elapsed = Date.now() - startTimeRef.current
      const rate    = sentRef.current / (elapsed / 1000)

      setStats({
        sent:       sentRef.current,
        errors:     errorsRef.current,
        elapsedMs:  elapsed,
        rateActual: Math.round(rate * 10) / 10,
        status:     'running'
      })

      addDataPoint('throughput', Math.round(rate))
      if (errorsRef.current > 0) addDataPoint('errors', errorsRef.current)
    }, batchWindow)
  }, [config])

  const stop = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current)
    setStats(prev => ({ ...prev, status: prev.sent >= config.totalMessages ? 'completed' : 'idle' }))
  }, [config.totalMessages])

  const pause = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current)
    setStats(prev => ({ ...prev, status: 'paused' }))
  }, [])

  const reset = useCallback(() => {
    stop()
    setStats({ sent: 0, errors: 0, elapsedMs: 0, rateActual: 0, status: 'idle' })
    setMetrics({ throughput: [], errors: [], distribution: [
      { name: 'Registration', value: 0 }, { name: 'Baseline', value: 0 }
    ]})
  }, [stop])

  return { config, setConfig, stats, metrics, start, stop, pause, reset }
}
```

---

## 6. Componentes de Configuración & Estadísticas

### `src/components/simulator/SimulatorControls.tsx`

```tsx
import type { SimulationConfig, SimulationStats } from '@/types/simulation'

interface Props {
  config:    SimulationConfig
  stats:     SimulationStats
  onChange:  (c: SimulationConfig) => void
  onStart:   () => void
  onPause:   () => void
  onStop:    () => void
  onReset:   () => void
}

export function SimulatorControls({ config, stats, onChange, onStart, onPause, onStop, onReset }: Props) {
  const isRunning = stats.status === 'running'
  const isPaused  = stats.status === 'paused'

  return (
    <div className="bg-white rounded-lg border p-4 space-y-4">
      <h3 className="font-semibold">Configuración</h3>

      <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
        {[
          { label: 'Total mensajes',   key: 'totalMessages',      min: 1,   max: 100000, step: 100 },
          { label: 'Mensajes/segundo', key: 'ratePerSecond',      min: 1,   max: 100,    step: 1   },
          { label: 'Ratio registration (0-1)', key: 'registrationRatio', min: 0, max: 1, step: 0.1 },
          { label: 'Variedad node_id', key: 'nodeIdCount',        min: 1,   max: 100,    step: 1   },
          { label: 'Variedad dispatch_unit', key: 'dispatchUnitCount', min: 1, max: 100, step: 1   },
        ].map(({ label, key, min, max, step }) => (
          <label key={key} className="space-y-1">
            <span className="text-sm text-gray-600">{label}</span>
            <input
              type="number"
              value={config[key as keyof SimulationConfig]}
              onChange={e => onChange({ ...config, [key]: parseFloat(e.target.value) })}
              disabled={isRunning || isPaused}
              min={min} max={max} step={step}
              className="w-full border rounded px-2 py-1 text-sm"
            />
          </label>
        ))}
      </div>

      <div className="flex gap-2 pt-2">
        {!isRunning && !isPaused && (
          <button onClick={onStart} className="btn-primary">▶ Iniciar</button>
        )}
        {isRunning && (
          <button onClick={onPause} className="btn-secondary">⏸ Pausar</button>
        )}
        {isPaused && (
          <button onClick={onStart} className="btn-primary">▶ Reanudar</button>
        )}
        {(isRunning || isPaused) && (
          <button onClick={onStop} className="btn-danger">■ Detener</button>
        )}
        <button onClick={onReset} className="btn-secondary">↺ Reset</button>
      </div>
    </div>
  )
}
```

### `src/components/simulator/SimulatorStats.tsx`

```tsx
import type { SimulationStats } from '@/types/simulation'

const statusColors = {
  idle:      'text-gray-500',
  running:   'text-green-600',
  paused:    'text-yellow-600',
  completed: 'text-blue-600',
  error:     'text-red-600'
}

export function SimulatorStats({ stats }: { stats: SimulationStats }) {
  const elapsed = (stats.elapsedMs / 1000).toFixed(1)

  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
      {[
        { label: 'Estado',     value: stats.status,      className: statusColors[stats.status] },
        { label: 'Enviados',   value: stats.sent,         className: 'text-gray-900' },
        { label: 'Errores',    value: stats.errors,       className: stats.errors > 0 ? 'text-red-600' : 'text-gray-900' },
        { label: 'Tiempo (s)', value: elapsed,            className: 'text-gray-900' },
        { label: 'Tasa real',  value: `${stats.rateActual} msg/s`, className: 'text-gray-900' },
      ].map(({ label, value, className }) => (
        <div key={label} className="bg-gray-50 rounded p-3 text-center">
          <p className="text-xs text-gray-500 mb-1">{label}</p>
          <p className={`font-bold text-lg ${className}`}>{value}</p>
        </div>
      ))}
    </div>
  )
}
```

---

## 7. Gráficas

### `src/components/simulator/charts/ThroughputChart.tsx`

```tsx
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { DataPoint } from '@/types/simulation'

export function ThroughputChart({ data }: { data: DataPoint[] }) {
  return (
    <div className="bg-white rounded-lg border p-4">
      <h4 className="text-sm font-medium text-gray-700 mb-3">Throughput (msgs/s)</h4>
      <ResponsiveContainer width="100%" height={200}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
          <XAxis dataKey="label" tick={{ fontSize: 10 }} />
          <YAxis tick={{ fontSize: 10 }} />
          <Tooltip />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#2563eb"
            strokeWidth={2}
            dot={false}
            name="msgs/s"
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
```

### `src/components/simulator/charts/DistributionChart.tsx`

```tsx
import { PieChart, Pie, Cell, Legend, Tooltip, ResponsiveContainer } from 'recharts'

const COLORS = ['#2563eb', '#16a34a']

export function DistributionChart({ data }: { data: { name: string; value: number }[] }) {
  return (
    <div className="bg-white rounded-lg border p-4">
      <h4 className="text-sm font-medium text-gray-700 mb-3">Distribución de Mensajes</h4>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie data={data} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={70} label>
            {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
          </Pie>
          <Tooltip />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
```

---

## 8. SimulationDashboard — Composición Final

### `src/components/simulator/SimulationDashboard.tsx`

```tsx
import { useSimulator } from '@/hooks/useSimulator'
import { SimulatorControls } from './SimulatorControls'
import { SimulatorStats } from './SimulatorStats'
import { ThroughputChart } from './charts/ThroughputChart'
import { DistributionChart } from './charts/DistributionChart'

export function SimulationDashboard() {
  const { config, setConfig, stats, metrics, start, stop, pause, reset } = useSimulator()

  return (
    <div className="space-y-6">
      <SimulatorControls
        config={config} stats={stats}
        onChange={setConfig}
        onStart={start} onPause={pause} onStop={stop} onReset={reset}
      />

      <SimulatorStats stats={stats} />

      {metrics.throughput.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ThroughputChart data={metrics.throughput} />
          <DistributionChart data={metrics.distribution} />
        </div>
      )}
    </div>
  )
}
```

---

## Criterios de Aceptación

- [ ] El simulador genera y publica mensajes al input-stream a la tasa configurada
- [ ] El ratio registration/baseline se respeta aproximadamente durante la simulación
- [ ] Los controles Start / Pause / Stop / Reset funcionan correctamente
- [ ] Las gráficas se actualizan en tiempo real durante la simulación
- [ ] El throughput chart muestra la tasa real de mensajes/segundo
- [ ] La distribución pie chart refleja la proporción real enviada
- [ ] El estado "Completado" aparece correctamente al terminar todos los mensajes
- [ ] `npm test` pasa los tests del simulador y las gráficas
