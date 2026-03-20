import { useResourceStatus } from '@/hooks/useResourceStatus'
import { ResourceCard } from './ResourceCard'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'

export function StatusPanel() {
  const { resources, loading, error, refresh } = useResourceStatus(10_000)

  if (loading && resources.length === 0) return <LoadingSpinner size="lg" />

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-gray-500">{resources.length} recursos monitorizados</p>
        <button onClick={refresh} className="btn-secondary text-xs">↺ Actualizar</button>
      </div>

      {error && (
        <div className="bg-yellow-50 border border-yellow-200 rounded p-3 text-sm text-yellow-800">
          ⚠ {error}
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {resources.map(r => (
          <ResourceCard key={`${r.type}-${r.name}`} resource={r} />
        ))}
        {resources.length === 0 && !loading && (
          <p className="text-gray-400 text-sm col-span-full text-center py-8">
            No se detectaron recursos. ¿Está LocalStack corriendo?
          </p>
        )}
      </div>
    </div>
  )
}
