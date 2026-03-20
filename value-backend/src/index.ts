import { buildServer } from './server.js'

const PORT = parseInt(process.env.PORT ?? '3333', 10)
const HOST = process.env.HOST ?? '0.0.0.0'

const app = buildServer()

try {
  await app.listen({ port: PORT, host: HOST })
} catch (err) {
  app.log.error(err)
  process.exit(1)
}
