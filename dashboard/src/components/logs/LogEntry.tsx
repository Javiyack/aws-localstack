import type { LogEvent } from '@/api/cloudwatch'

interface Props { event: LogEvent }

function detectLevel(msg: string): { label: string; cls: string } {
  const u = msg.toUpperCase()
  if (u.includes('ERROR') || u.includes('EXCEPTION')) return { label: 'ERROR', cls: 'text-red-700 bg-red-50'     }
  if (u.includes('WARN'))                              return { label: 'WARN',  cls: 'text-yellow-700 bg-yellow-50' }
  if (u.includes('DEBUG'))                             return { label: 'DEBUG', cls: 'text-gray-500 bg-gray-50'   }
  return                                                      { label: 'INFO',  cls: 'text-blue-700 bg-blue-50'   }
}

export function LogEntry({ event }: Props) {
  const { label, cls } = detectLevel(event.message)
  const ts = new Date(event.timestamp).toLocaleTimeString()
  return (
    <div className={`flex gap-3 px-3 py-1.5 font-mono text-xs rounded mb-0.5 ${cls}`}>
      <span className="shrink-0 opacity-60">{ts}</span>
      <span className="shrink-0 font-bold w-12">{label}</span>
      <span className="flex-1 break-all whitespace-pre-wrap">{event.message.trim()}</span>
    </div>
  )
}
