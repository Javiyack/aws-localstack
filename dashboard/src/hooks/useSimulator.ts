import { useState, useRef, useCallback } from 'react'
import { putRecord } from '@/api/kinesis'
import { generateMessage } from '@/services/messageGenerator'
import type { SimulationConfig, SimulationStats, SimulationMetrics, DataPoint } from '@/types/simulation'

const DEFAULT_CONFIG: SimulationConfig = {
  totalMessages:     1000,
  ratePerSecond:     10,
  registrationRatio: 0.5,
  nodeIdCount:       20,
  dispatchUnitCount: 10
}

export function useSimulator() {
  const [config, setConfig]   = useState<SimulationConfig>(DEFAULT_CONFIG)
  const [stats, setStats]     = useState<SimulationStats>({
    sent: 0, errors: 0, elapsedMs: 0, rateActual: 0, status: 'idle'
  })
  const [metrics, setMetrics] = useState<SimulationMetrics>({
    throughput:   [],
    errors:       [],
    distribution: [{ name: 'Registration', value: 0 }, { name: 'Baseline', value: 0 }]
  })

  const intervalRef  = useRef<ReturnType<typeof setInterval> | null>(null)
  const startTimeRef = useRef(0)
  const sentRef      = useRef(0)
  const errorsRef    = useRef(0)

  const addPoint = (type: 'throughput' | 'errors', value: number) => {
    const point: DataPoint = { timestamp: Date.now(), value, label: new Date().toLocaleTimeString() }
    setMetrics(prev => ({ ...prev, [type]: [...prev[type].slice(-60), point] }))
  }

  const stop = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current)
    setStats(prev => ({
      ...prev,
      status: prev.sent >= config.totalMessages ? 'completed' : 'idle'
    }))
  }, [config.totalMessages])

  const start = useCallback(() => {
    sentRef.current   = 0
    errorsRef.current = 0
    startTimeRef.current = Date.now()

    setStats({ sent: 0, errors: 0, elapsedMs: 0, rateActual: 0, status: 'running' })
    setMetrics({
      throughput: [], errors: [],
      distribution: [{ name: 'Registration', value: 0 }, { name: 'Baseline', value: 0 }]
    })

    const TICK_MS = 100

    intervalRef.current = setInterval(() => {
      if (sentRef.current >= config.totalMessages) { stop(); return }

      const msgsThisTick = Math.min(
        Math.ceil(config.ratePerSecond * TICK_MS / 1000),
        config.totalMessages - sentRef.current
      )

      void Promise.allSettled(
        Array.from({ length: msgsThisTick }, async () => {
          try {
            const msg = generateMessage(config)
            await putRecord('input-stream', msg)
            sentRef.current++
            setMetrics(prev => ({
              ...prev,
              distribution: prev.distribution.map(d =>
                (d.name === 'Registration' && !!msg.registrationId) ||
                (d.name === 'Baseline'     && !!msg.baselineId)
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
        sent: sentRef.current, errors: errorsRef.current,
        elapsedMs: elapsed, rateActual: Math.round(rate * 10) / 10, status: 'running'
      })
      addPoint('throughput', Math.round(rate))
      if (errorsRef.current > 0) addPoint('errors', errorsRef.current)
    }, TICK_MS)
  }, [config, stop])

  const pause = useCallback(() => {
    if (intervalRef.current) clearInterval(intervalRef.current)
    setStats(prev => ({ ...prev, status: 'paused' }))
  }, [])

  const reset = useCallback(() => {
    stop()
    setStats({ sent: 0, errors: 0, elapsedMs: 0, rateActual: 0, status: 'idle' })
    setMetrics({
      throughput: [], errors: [],
      distribution: [{ name: 'Registration', value: 0 }, { name: 'Baseline', value: 0 }]
    })
  }, [stop])

  return { config, setConfig, stats, metrics, start, stop, pause, reset }
}
