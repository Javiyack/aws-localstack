import type { SimulationStats, SimulationConfig } from '@/types/simulation'

const STATUS_LABELS: Record<SimulationStats['status'], { label: string; cls: string }> = {
  idle:      { label: 'Listo',      cls: 'text-gray-500'               },
  running:   { label: 'Ejecutando', cls: 'text-green-600 animate-pulse' },
  paused:    { label: 'Pausado',    cls: 'text-yellow-600'              },
  completed: { label: 'Completado', cls: 'text-blue-600'               },
  error:     { label: 'Error',      cls: 'text-red-600'                }
}

interface Props { stats: SimulationStats; config: SimulationConfig }

export function SimulatorStats({ stats, config }: Props) {
  const { label, cls } = STATUS_LABELS[stats.status]
  const progress = config.totalMessages > 0
    ? Math.min(100, Math.round(stats.sent / config.totalMessages * 100))
    : 0

  return (
    <div className="bg-white rounded-lg border p-4 space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">Estado</h3>
        <span className={`text-sm font-medium ${cls}`}>{label}</span>
      </div>

      <div className="w-full bg-gray-200 rounded-full h-2">
        <div
          className="bg-blue-600 h-2 rounded-full transition-all duration-300"
          style={{ width: `${progress}%` }}
        />
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: 'Enviados',  value: stats.sent.toLocaleString()    },
          { label: 'Errores',   value: stats.errors.toLocaleString()  },
          { label: 'Tiempo',    value: `${(stats.elapsedMs / 1000).toFixed(1)}s` },
          { label: 'Rate real', value: `${stats.rateActual} msg/s`   }
        ].map(({ label: lbl, value }) => (
          <div key={lbl} className="text-center">
            <div className="text-lg font-mono font-semibold text-gray-900">{value}</div>
            <div className="text-xs text-gray-500">{lbl}</div>
          </div>
        ))}
      </div>

      <p className="text-xs text-gray-400 text-right">
        Progreso: {progress}% ({stats.sent.toLocaleString()} / {config.totalMessages.toLocaleString()})
      </p>
    </div>
  )
}
