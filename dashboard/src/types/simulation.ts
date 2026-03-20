export interface SimulationConfig {
  totalMessages:      number   // total de mensajes a enviar
  ratePerSecond:      number   // mensajes/segundo
  registrationRatio:  number   // 0-1 (proporción registration vs baseline)
  nodeIdCount:        number   // variedad de node_id (1-100)
  dispatchUnitCount:  number   // variedad de dispatch_unit (1-50)
}

export interface SimulationStats {
  sent:       number
  errors:     number
  elapsedMs:  number
  rateActual: number   // msgs/s reales
  status:     'idle' | 'running' | 'paused' | 'completed' | 'error'
}

export interface DataPoint {
  timestamp: number   // ms epoch
  value:     number
  label:     string
}

export interface SimulationMetrics {
  throughput:   DataPoint[]
  errors:       DataPoint[]
  distribution: { name: string; value: number }[]
}
