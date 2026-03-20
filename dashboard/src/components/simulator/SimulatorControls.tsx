import type { SimulationConfig, SimulationStats } from '@/types/simulation'

interface Field {
  label: string
  key:   keyof SimulationConfig
  min:   number
  max:   number
  step:  number
}

const FIELDS: Field[] = [
  { label: 'Total mensajes',          key: 'totalMessages',      min: 1,  max: 100000, step: 100  },
  { label: 'Mensajes/segundo',        key: 'ratePerSecond',      min: 1,  max: 100,    step: 1    },
  { label: 'Ratio registration (0-1)',key: 'registrationRatio',  min: 0,  max: 1,      step: 0.05 },
  { label: 'Variedad node_id',        key: 'nodeIdCount',        min: 1,  max: 100,    step: 1    },
  { label: 'Variedad dispatch_unit',  key: 'dispatchUnitCount',  min: 1,  max: 50,     step: 1    }
]

interface Props {
  config:   SimulationConfig
  stats:    SimulationStats
  onChange: (c: SimulationConfig) => void
  onStart:  () => void
  onPause:  () => void
  onStop:   () => void
  onReset:  () => void
}

export function SimulatorControls({ config, stats, onChange, onStart, onPause, onStop, onReset }: Props) {
  const isRunning = stats.status === 'running'
  const isPaused  = stats.status === 'paused'
  const isIdle    = stats.status === 'idle' || stats.status === 'completed'

  return (
    <div className="bg-white rounded-lg border p-4 space-y-4">
      <h3 className="font-semibold">Configuración</h3>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {FIELDS.map(({ label, key, min, max, step }) => (
          <label key={key} className="block">
            <span className="text-xs text-gray-600 mb-1 block">{label}</span>
            <input
              type="number"
              value={config[key]}
              min={min} max={max} step={step}
              disabled={isRunning || isPaused}
              onChange={e => onChange({ ...config, [key]: Number(e.target.value) })}
              className="w-full border rounded px-2 py-1 text-sm disabled:bg-gray-50"
            />
          </label>
        ))}
      </div>

      <div className="flex gap-2 pt-2 border-t flex-wrap">
        {isIdle    && <button onClick={onStart} className="btn-primary">▶ Iniciar</button>}
        {isRunning && <button onClick={onPause} className="btn-secondary">⏸ Pausar</button>}
        {isPaused  && <button onClick={onStart} className="btn-primary">▶ Continuar</button>}
        {(isRunning || isPaused) && (
          <button onClick={onStop} className="btn-danger">■ Detener</button>
        )}
        <button onClick={onReset} className="btn-secondary" disabled={isRunning}>
          ↺ Reset
        </button>
      </div>
    </div>
  )
}
