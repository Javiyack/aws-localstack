import { Header } from '@/components/layout/Header'
import { StatusPanel } from '@/components/status/StatusPanel'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'

export function StatusPage() {
  return (
    <>
      <Header
        title="Estado de Recursos"
        subtitle="Monitorización en tiempo real de los servicios AWS locales"
      />
      <ErrorBoundary>
        <StatusPanel />
      </ErrorBoundary>
    </>
  )
}
