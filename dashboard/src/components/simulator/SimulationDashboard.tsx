import { useSimulator } from '@/hooks/useSimulator'
import { SimulatorControls } from './SimulatorControls'
import { SimulatorStats } from './SimulatorStats'
import { ThroughputChart } from './charts/ThroughputChart'
import { DistributionChart } from './charts/DistributionChart'

export function SimulationDashboard() {
  const { config, setConfig, stats, metrics, start, stop, pause, reset } = useSimulator()

  return (
    <div className="space-y-4">
      <SimulatorControls
        config={config}
        stats={stats}
        onChange={setConfig}
        onStart={start}
        onPause={pause}
        onStop={stop}
        onReset={reset}
      />
      <SimulatorStats stats={stats} config={config} />
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <ThroughputChart data={metrics.throughput} />
        <DistributionChart data={metrics.distribution} />
      </div>
    </div>
  )
}
