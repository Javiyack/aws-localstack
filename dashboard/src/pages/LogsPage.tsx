import { Header } from '@/components/layout/Header'
import { LogViewer } from '@/components/logs/LogViewer'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'

export function LogsPage() {
  return (
    <>
      <Header
        title="Logs CloudWatch"
        subtitle="Visor de eventos de la función Lambda y servicios AWS"
      />
      <ErrorBoundary>
        <div className="bg-white rounded-lg border p-4">
          <LogViewer />
        </div>
      </ErrorBoundary>
    </>
  )
}
