import type { SimulationConfig } from '@/types/simulation'
import type { InputMessage } from '@/types/messages'

function randomId(prefix: string, count: number): string {
  const n = Math.floor(Math.random() * count) + 1
  return `${prefix}-${String(n).padStart(3, '0')}`
}

export function generateMessage(config: SimulationConfig): InputMessage {
  const isRegistration = Math.random() < config.registrationRatio
  const nodeId         = randomId('node', config.nodeIdCount)
  const dttmUtc        = new Date().toISOString()

  return isRegistration
    ? { nodeId, dttmUtc, registrationId: randomId('reg', config.dispatchUnitCount) }
    : { nodeId, dttmUtc, baselineId:     randomId('base', config.dispatchUnitCount) }
}
