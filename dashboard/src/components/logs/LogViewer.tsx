import { useEffect } from 'react'
import { useCloudWatchLogs } from '@/hooks/useCloudWatchLogs'
import { LogEntry } from './LogEntry'
import { LogLevelFilter } from './LogLevelFilter'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'

export function LogViewer() {
  const {
    logGroups, logStreams, events,
    selectedGroup, selectedStream, level,
    loading, error,
    loadGroups, selectGroup, selectStream, setLevel, refresh
  } = useCloudWatchLogs()

  useEffect(() => { void loadGroups() }, [loadGroups])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-3 items-center">
        <select
          value={selectedGroup}
          onChange={e => void selectGroup(e.target.value)}
          className="border rounded px-3 py-1.5 text-sm min-w-48"
        >
          <option value="">— Grupo de logs —</option>
          {logGroups.map(g => <option key={g} value={g}>{g}</option>)}
        </select>

        {logStreams.length > 0 && (
          <select
            value={selectedStream}
            onChange={e => void selectStream(e.target.value)}
            className="border rounded px-3 py-1.5 text-sm min-w-48"
          >
            {logStreams.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
        )}

        <button onClick={refresh} className="btn-secondary text-xs">↺ Refrescar</button>
      </div>

      <LogLevelFilter selected={level} onChange={setLevel} />

      {error && <p className="text-red-600 text-sm">{error}</p>}

      {loading ? (
        <LoadingSpinner />
      ) : events.length === 0 ? (
        <div className="text-center py-12 text-gray-400 text-sm">
          {selectedGroup
            ? 'Sin eventos para los filtros seleccionados.'
            : 'Selecciona un grupo de logs.'}
        </div>
      ) : (
        <div className="bg-gray-50 rounded border max-h-[600px] overflow-y-auto p-2">
          {events.map((e, i) => (
            <LogEntry key={`${e.timestamp}-${i}`} event={e} />
          ))}
        </div>
      )}
    </div>
  )
}
