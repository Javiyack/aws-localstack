import Fastify from 'fastify'

export function buildServer() {
  const app = Fastify({ logger: true })

  // Health check
  app.get('/health', async (_request, reply) => {
    return reply.send({ status: 'ok' })
  })

  // Endpoint principal: devuelve un valor aleatorio entre 900 y 1100
  app.get<{ Params: { id: string } }>('/:id', async (request, reply) => {
    const { id } = request.params

    if (!id || id.trim() === '') {
      return reply.status(400).send({ error: 'id is required' })
    }

    const value = 900 + Math.random() * 200  // [900, 1100)
    return reply.send({ value: parseFloat(value.toFixed(4)) })
  })

  return app
}
