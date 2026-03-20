import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { buildServer } from '../src/server.js'
import type { FastifyInstance } from 'fastify'

let app: FastifyInstance

beforeAll(async () => {
  app = buildServer()
  await app.ready()
})

afterAll(async () => {
  await app.close()
})

describe('GET /health', () => {
  it('devuelve status ok', async () => {
    const res = await app.inject({ method: 'GET', url: '/health' })
    expect(res.statusCode).toBe(200)
    expect(JSON.parse(res.body)).toEqual({ status: 'ok' })
  })
})

describe('GET /:id', () => {
  it('devuelve un value entre 900 y 1100', async () => {
    const res = await app.inject({ method: 'GET', url: '/reg-001' })
    expect(res.statusCode).toBe(200)
    const body = JSON.parse(res.body)
    expect(body).toHaveProperty('value')
    expect(typeof body.value).toBe('number')
    expect(body.value).toBeGreaterThanOrEqual(900)
    expect(body.value).toBeLessThan(1100)
  })

  it('el value tiene máximo 4 decimales', async () => {
    const res = await app.inject({ method: 'GET', url: '/reg-001' })
    const { value } = JSON.parse(res.body)
    const decimals = value.toString().split('.')[1]?.length ?? 0
    expect(decimals).toBeLessThanOrEqual(4)
  })

  it('acepta distintos formatos de id', async () => {
    const ids = ['abc123', 'node-99', 'baseline_xyz', 'REG-001', '12345']
    for (const id of ids) {
      const res = await app.inject({ method: 'GET', url: `/${id}` })
      expect(res.statusCode).toBe(200)
    }
  })

  it('los valores son aleatorios (no siempre iguales)', async () => {
    const values = await Promise.all(
      Array.from({ length: 20 }, () =>
        app.inject({ method: 'GET', url: '/test-randomness' })
          .then(r => JSON.parse(r.body).value as number)
      )
    )
    const unique = new Set(values)
    expect(unique.size).toBeGreaterThan(1)
  })

  it('responde a id con guiones y underscores', async () => {
    const res = await app.inject({ method: 'GET', url: '/my-registration_id-001' })
    expect(res.statusCode).toBe(200)
    const body = JSON.parse(res.body)
    expect(body.value).toBeGreaterThanOrEqual(900)
  })
})
