import type { LogLevel } from '@/api/cloudwatch'

const LEVELS: LogLevel[] = ['ALL', 'ERROR', 'WARN', 'INFO', 'DEBUG']

interface Props {
  selected: LogLevel
  onChange: (l: LogLevel) => void
}

export function LogLevelFilter({ selected, onChange }: Props) {
  return (
    <div className="flex gap-1 flex-wrap">
      {LEVELS.map(l => (
        <button
          key={l}
          onClick={() => onChange(l)}
          className={`px-2.5 py-1 rounded text-xs font-medium transition-colors ${
            selected === l
              ? 'bg-gray-800 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
          }`}
        >
          {l}
        </button>
      ))}
    </div>
  )
}
