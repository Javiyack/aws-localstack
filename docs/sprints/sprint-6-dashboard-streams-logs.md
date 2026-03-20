# Sprint 6 — Dashboard: Gestión de Streams & Visor de Logs

## Objetivo

Implementar los módulos de lectura/escritura de streams Kinesis y el visor de logs de CloudWatch, dando capacidad de inspección y prueba manual del pipeline.

**Duración estimada:** 1 semana  
**Dependencias:** Sprint 4 (pipeline funcional), Sprint 5 (dashboard base)

---

## 1. Páginas a Implementar

```
dashboard/src/
├── pages/
│   ├── StreamsPage.tsx      ← nuevo
│   └── LogsPage.tsx         ← nuevo
├── components/
│   ├── streams/
│   │   ├── StreamReader.tsx
│   │   ├── StreamWriter.tsx
│   │   ├── MessageList.tsx
│   │   ├── MessageItem.tsx
│   │   └── StreamSelector.tsx
│   └── logs/
│       ├── LogViewer.tsx
│       ├── LogEntry.tsx
│       └── LogLevelFilter.tsx
├── hooks/
│   ├── useStreamReader.ts
│   └── useCloudWatchLogs.ts
└── api/
    ├── kinesis.ts           ← ampliar
    └── cloudwatch.ts        ← nuevo
```

---

## 2. API — Kinesis

### `src/api/kinesis.ts`

```typescript
import { localstackClient } from './client'
import type { InputMessage, PerformanceInterval } from '@/types/messages'

export type AnyStreamMessage = InputMessage | PerformanceInterval | Record<string, unknown>

export interface StreamRecord {
  sequenceNumber: string
  approximateArrivalTimestamp: number
  data: AnyStreamMessage
  raw: string
}

export type StreamName = 'input-stream' | 'output-stream'

// Obtener shard iterator
async function getShardIterator(streamName: StreamName): Promise<string> {
  const res = await localstackClient.post('/kinesis', {
    StreamName:        streamName,
    ShardId:           'shardId-000000000000',
    ShardIteratorType: 'LATEST'
  }, {
    headers: { 'X-Amz-Target': 'Kinesis_20131202.GetShardIterator' }
  })
  return res.data.ShardIterator
}

// Leer registros desde un iterator
export async function getRecords(iterator: string, limit = 100): Promise<{
  records: StreamRecord[]
  nextIterator: string | null
}> {
  const res = await localstackClient.post('/kinesis', {
    ShardIterator: iterator,
    Limit: limit
  }, {
    headers: { 'X-Amz-Target': 'Kinesis_20131202.GetRecords' }
  })

  const records: StreamRecord[] = res.data.Records.map((r: any) => {
    const raw = atob(r.Data)
    let data: AnyStreamMessage
    try { data = JSON.parse(raw) } catch { data = { raw } }
    return {
      sequenceNumber: r.SequenceNumber,
      approximateArrivalTimestamp: r.ApproximateArrivalTimestamp,
      data,
      raw
    }
  })

  return { records, nextIterator: res.data.NextShardIterator ?? null }
}

// Publicar un mensaje
export async function putRecord(streamName: StreamName, message: InputMessage): Promise<void> {
  const data = btoa(JSON.stringify(message))
  await localstackClient.post('/kinesis', {
    StreamName:   streamName,
    Data:         data,
    PartitionKey: message.nodeId ?? 'default'
  }, {
    headers: { 'X-Amz-Target': 'Kinesis_20131202.PutRecord' }
  })
}

export { getShardIterator }
```

---

## 3. Hook de Lectura de Stream

### `src/hooks/useStreamReader.ts`

```typescript
import { useState, useCallback, useRef } from 'react'
import { getShardIterator, getRecords } from '@/api/kinesis'
import type { StreamRecord, StreamName } from '@/api/kinesis'

export function useStreamReader(streamName: StreamName) {
  const [records, setRecords]       = useState<StreamRecord[]>([])
  const [reading, setReading]       = useState(false)
  const [error, setError]           = useState<string | null>(null)
  const iteratorRef                  = useRef<string | null>(null)
  const intervalRef                  = useRef<ReturnType<typeof setInterval> | null>(null)

  const start = useCallback(async () => {
    try {
      setReading(true)
      setError(null)
      iteratorRef.current = await getShardIterator(streamName)

      intervalRef.current = setInterval(async () => {
        if (!iteratorRef.current) return
        const { records: newRecords, nextIterator } = await getRecords(iteratorRef.current)
        if (newRecords.length > 0) {
          setRecords(prev => [...newRecords, ...prev].slice(0, 500)) // max 500 en UI
        }
        iteratorRef.current = nextIterator
      }, 2000)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error leyendo stream')
      setReading(false)
    }
  }, [streamName])

  const stop = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current)
    setReading(false)
  }, [])

  const clear = useCallback(() => setRecords([]), [])

  return { records, reading, error, start, stop, clear }
}
```

---

## 4. Componente StreamReader

### `src/components/streams/StreamReader.tsx`

```tsx
import { useState } from 'react'
import { useStreamReader } from '@/hooks/useStreamReader'
import { MessageList } from './MessageList'
import type { StreamName } from '@/api/kinesis'

export function StreamReader() {
  const [selected, setSelected] = useState<StreamName>('output-stream')
  const { records, reading, error, start, stop, clear } = useStreamReader(selected)

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <select
          value={selected}
          onChange={e => setSelected(e.target.value as StreamName)}
          disabled={reading}
          className="border rounded px-3 py-1.5 text-sm"
        >
          <option value="input-stream">input-stream</option>
          <option value="output-stream">output-stream</option>
        </select>

        {!reading ? (
          <button onClick={start} className="btn-primary">▶ Iniciar lectura</button>
        ) : (
          <button onClick={stop} className="btn-danger">■ Detener</button>
        )}
        <button onClick={clear} className="btn-secondary" disabled={records.length === 0}>
          Limpiar
        </button>
        <span className="text-sm text-gray-500">{records.length} mensajes</span>
      </div>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      <MessageList records={records} />
    </div>
  )
}
```

---

## 5. Componente StreamWriter

### `src/components/streams/StreamWriter.tsx`

```tsx
import { useState } from 'react'
import { putRecord } from '@/api/kinesis'
import type { InputMessage } from '@/types/messages'

const TEMPLATES = {
  registration: {
    nodeId: 'node-001',
    dttmUtc: new Date().toISOString(),
    registrationId: 'reg-001',
    baselineId: undefined
  },
  baseline: {
    nodeId: 'node-001',
    dttmUtc: new Date().toISOString(),
    registrationId: undefined,
    baselineId: 'base-001'
  }
}

export function StreamWriter() {
  const [json, setJson]       = useState(JSON.stringify(TEMPLATES.registration, null, 2))
  const [sending, setSending] = useState(false)
  const [feedback, setFeedback] = useState<{ type: 'ok' | 'error'; msg: string } | null>(null)

  const setTemplate = (t: keyof typeof TEMPLATES) =>
    setJson(JSON.stringify({ ...TEMPLATES[t], dttmUtc: new Date().toISOString() }, null, 2))

  const send = async () => {
    try {
      setSending(true)
      setFeedback(null)
      const message: InputMessage = JSON.parse(json)

      // Validar mutuamente excluyentes
      if (!!message.registrationId === !!message.baselineId) {
        throw new Error('El mensaje debe tener exactamente uno de: registrationId o baselineId')
      }

      await putRecord('input-stream', message)
      setFeedback({ type: 'ok', msg: 'Mensaje publicado correctamente' })
    } catch (e) {
      setFeedback({ type: 'error', msg: e instanceof Error ? e.message : 'Error desconocido' })
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        <button onClick={() => setTemplate('registration')} className="btn-secondary text-xs">
          Template: Registration
        </button>
        <button onClick={() => setTemplate('baseline')} className="btn-secondary text-xs">
          Template: Baseline
        </button>
      </div>

      <textarea
        value={json}
        onChange={e => setJson(e.target.value)}
        rows={12}
        className="w-full font-mono text-sm border rounded p-3 bg-gray-50"
      />

      {feedback && (
        <p className={`text-sm ${feedback.type === 'ok' ? 'text-green-700' : 'text-red-700'}`}>
          {feedback.type === 'ok' ? '✓' : '✗'} {feedback.msg}
        </p>
      )}

      <button onClick={send} disabled={sending} className="btn-primary">
        {sending ? 'Enviando…' : '➤ Publicar en input-stream'}
      </button>
    </div>
  )
}
```

---

## 6. API CloudWatch & Visor de Logs

### `src/api/cloudwatch.ts`

```typescript
import { localstackClient } from './client'

export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'ALL'

export interface LogEvent {
  timestamp:    number
  message:      string
  level:        LogLevel
  ingestionTime: number
}

export async function getLogEvents(
  logGroupName: string,
  limit = 200
): Promise<LogEvent[]> {
  // 1. Obtener log streams
  const streamsRes = await localstackClient.post('/logs', {
    logGroupName,
    orderBy: 'LastEventTime',
    descending: true,
    limit: 1
  }, { headers: { 'X-Amz-Target': 'Logs_20140328.DescribeLogStreams' } })

  const streams = streamsRes.data.logStreams
  if (!streams?.length) return []

  // 2. Obtener eventos del stream más reciente
  const eventsRes = await localstackClient.post('/logs', {
    logGroupName,
    logStreamName: streams[0].logStreamName,
    limit,
    startFromHead: false
  }, { headers: { 'X-Amz-Target': 'Logs_20140328.GetLogEvents' } })

  return eventsRes.data.events.map((e: any) => ({
    timestamp:    e.timestamp,
    message:      e.message,
    level:        detectLevel(e.message),
    ingestionTime: e.ingestionTime
  }))
}

function detectLevel(message: string): LogLevel {
  if (message.includes('[ERROR]') || message.includes('ERROR')) return 'ERROR'
  if (message.includes('[WARN]')  || message.includes('WARN'))  return 'WARN'
  return 'INFO'
}
```

### `src/components/logs/LogViewer.tsx`

```tsx
import { useState, useEffect, useRef } from 'react'
import { getLogEvents } from '@/api/cloudwatch'
import { LogEntry } from './LogEntry'
import { LogLevelFilter } from './LogLevelFilter'
import type { LogEvent, LogLevel } from '@/api/cloudwatch'

const LOG_GROUP = '/aws/lambda/pipeline-processor'

export function LogViewer() {
  const [events, setEvents]       = useState<LogEvent[]>([])
  const [filter, setFilter]       = useState<LogLevel>('ALL')
  const [autoScroll, setAutoScroll] = useState(true)
  const [loading, setLoading]     = useState(false)
  const bottomRef                  = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      try {
        const data = await getLogEvents(LOG_GROUP)
        setEvents(data)
      } finally {
        setLoading(false)
      }
    }
    load()
    const id = setInterval(load, 5000)
    return () => clearInterval(id)
  }, [])

  useEffect(() => {
    if (autoScroll) bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [events, autoScroll])

  const visible = filter === 'ALL'
    ? events
    : events.filter(e => e.level === filter)

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <LogLevelFilter value={filter} onChange={setFilter} />
        <label className="flex items-center gap-1.5 text-sm">
          <input type="checkbox" checked={autoScroll}
            onChange={e => setAutoScroll(e.target.checked)} />
          Auto-scroll
        </label>
        <span className="text-sm text-gray-500 ml-auto">{visible.length} entradas</span>
      </div>

      <div className="bg-gray-900 rounded-lg h-[500px] overflow-y-auto font-mono text-xs p-3 space-y-0.5">
        {loading && events.length === 0 && (
          <p className="text-gray-400">Cargando logs…</p>
        )}
        {visible.map((e, i) => <LogEntry key={i} event={e} />)}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
```

### `src/components/logs/LogEntry.tsx`

```tsx
import type { LogEvent } from '@/api/cloudwatch'

const colors = {
  ERROR: 'text-red-400',
  WARN:  'text-yellow-400',
  INFO:  'text-green-400',
  ALL:   'text-gray-300'
}

export function LogEntry({ event }: { event: LogEvent }) {
  const time = new Date(event.timestamp).toISOString().slice(11, 23)
  return (
    <div className="flex gap-2 leading-5">
      <span className="text-gray-500 select-none shrink-0">{time}</span>
      <span className={`${colors[event.level]} shrink-0`}>[{event.level}]</span>
      <span className="text-gray-200 break-all">{event.message}</span>
    </div>
  )
}
```

---

## 7. Rutas — Actualizar App.tsx

```tsx
// Añadir imports y rutas:
import { StreamsPage }  from '@/pages/StreamsPage'
import { LogsPage }     from '@/pages/LogsPage'

// Dentro de <Routes>:
<Route path="/streams"  element={<StreamsPage />} />
<Route path="/logs"     element={<LogsPage />} />
```

---

## Criterios de Aceptación

- [ ] StreamReader muestra mensajes del output-stream en tiempo real (polling cada 2s)
- [ ] StreamWriter publica mensajes al input-stream y confirma visualmente
- [ ] La validación en el cliente rechaza mensajes con ambos IDs o ninguno
- [ ] El visor de logs muestra entradas de CloudWatch de la Lambda
- [ ] El filtro por nivel (INFO/WARN/ERROR) funciona correctamente
- [ ] Auto-scroll mantiene la vista en los logs más recientes
- [ ] El botón Pausa detiene el auto-scroll sin detener la carga de logs
- [ ] `npm test` pasa los tests de los nuevos componentes
