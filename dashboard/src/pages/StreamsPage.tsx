import { Header } from '@/components/layout/Header'
import { StreamReader } from '@/components/streams/StreamReader'
import { StreamWriter } from '@/components/streams/StreamWriter'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'

export function StreamsPage() {
  return (
    <>
      <Header
        title="Streams Kinesis"
        subtitle="Lectura y escritura manual del pipeline de mensajes"
      />
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <ErrorBoundary>
          <section className="bg-white rounded-lg border p-4">
            <h3 className="font-semibold mb-4">Lector de stream</h3>
            <StreamReader />
          </section>
        </ErrorBoundary>
        <ErrorBoundary>
          <section className="bg-white rounded-lg border p-4">
            <h3 className="font-semibold mb-4">Publicar mensaje</h3>
            <StreamWriter />
          </section>
        </ErrorBoundary>
      </div>
    </>
  )
}
