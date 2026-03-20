import { describe, it, expect } from 'vitest'
import { generateMessage } from '../messageGenerator'
import type { SimulationConfig } from '@/types/simulation'

const cfg: SimulationConfig = {
  totalMessages:     100,
  ratePerSecond:     10,
  registrationRatio: 0.5,
  nodeIdCount:       10,
  dispatchUnitCount: 5
}

describe('generateMessage', () => {
  it('genera nodeId con formato node-NNN', () => {
    const msg = generateMessage(cfg)
    expect(msg.nodeId).toMatch(/^node-\d{3}$/)
  })

  it('genera dttmUtc en formato ISO-8601', () => {
    const msg = generateMessage(cfg)
    expect(msg.dttmUtc).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/)
  })

  it('genera exactamente un ID (registrationId XOR baselineId)', () => {
    for (let i = 0; i < 50; i++) {
      const msg = generateMessage(cfg)
      const hasReg  = !!msg.registrationId
      const hasBase = !!msg.baselineId
      expect(hasReg !== hasBase).toBe(true)
    }
  })

  it('respeta nodeIdCount (máximo N nodos distintos)', () => {
    const ids = new Set<string>()
    for (let i = 0; i < 200; i++) {
      ids.add(generateMessage({ ...cfg, nodeIdCount: 5 }).nodeId)
    }
    expect(ids.size).toBeLessThanOrEqual(5)
  })

  it('ratio 1.0 → solo registrationId', () => {
    for (let i = 0; i < 20; i++) {
      const msg = generateMessage({ ...cfg, registrationRatio: 1.0 })
      expect(msg.registrationId).toBeDefined()
      expect(msg.baselineId).toBeUndefined()
    }
  })

  it('ratio 0.0 → solo baselineId', () => {
    for (let i = 0; i < 20; i++) {
      const msg = generateMessage({ ...cfg, registrationRatio: 0.0 })
      expect(msg.baselineId).toBeDefined()
      expect(msg.registrationId).toBeUndefined()
    }
  })
})
