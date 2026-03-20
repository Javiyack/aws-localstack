import { useState } from 'react'
import { useStreamReader } from '@/hooks/useStreamReader'
import { MessageList } from './MessageList'
import type { StreamName } from '@/api/kinesis'

export function StreamReader() {
  const [selected, setSelected] = useState<StreamName>('output-stream')
  const { records, reading, error, start, stop, clear } = useStreamReader(selected)

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3 flex-wrap">
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
