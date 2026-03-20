export interface InputMessage {
  nodeId:          string
  dttmUtc:         string   // ISO-8601
  registrationId?: string
  baselineId?:     string
}

export interface PerformanceInterval {
  dispatchUnit: string
  dttmUtc:      string
  minValue:     number
  maxValue:     number
  sumValue:     number
  count:        number
}
