import { useState, useCallback, useRef } from 'react'
import { getShardIterator, getRecords } from '@/api/kinesis'
import type { StreamRecord, StreamName } from '@/api/kinesis'

export function useStreamReader(streamName: StreamName) {
  const [records, setRecords] = useState<StreamRecord[]>([])
  const [reading, setReading] = useState(false)
  const [error, setError]     = useState<string | null>(null)
  const iteratorRef           = useRef<string | null>(null)
  const intervalRef           = useRef<ReturnType<typeof setInterval> | null>(null)

  const start = useCallback(async () => {
    try {
      setReading(true)
      setError(null)
      iteratorRef.current = await getShardIterator(streamName)

      intervalRef.current = setInterval(async () => {
        if (!iteratorRef.current) return
        const { records: newRecords, nextIterator } = await getRecords(iteratorRef.current)
        if (newRecords.length > 0) {
          setRecords(prev => [...newRecords, ...prev].slice(0, 500))
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
