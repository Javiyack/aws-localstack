import { Header } from '@/components/layout/Header'
import { SimulationDashboard } from '@/components/simulator/SimulationDashboard'
import { ErrorBoundary } from '@/components/common/ErrorBoundary'

export function SimulatorPage() {
  return (
    <>
      <Header
        title="Simulador de Carga"
        subtitle="Genera mensajes sintéticos para probar el pipeline end-to-end"
      />
      <ErrorBoundary>
        <SimulationDashboard />
      </ErrorBoundary>
    </>
  )
}
