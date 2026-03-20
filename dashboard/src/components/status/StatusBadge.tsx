import type { ResourceStatus } from '@/types/resources'

const styles: Record<ResourceStatus, string> = {
  healthy:  'bg-green-100 text-green-800',
  degraded: 'bg-yellow-100 text-yellow-800',
  error:    'bg-red-100 text-red-800',
  unknown:  'bg-gray-100 text-gray-600'
}

const labels: Record<ResourceStatus, string> = {
  healthy:  'Operativo',
  degraded: 'Degradado',
  error:    'Error',
  unknown:  'Desconocido'
}

interface Props { status: ResourceStatus }

export function StatusBadge({ status }: Props) {
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${styles[status]}`}
    >
      <span className="w-1.5 h-1.5 rounded-full mr-1.5 bg-current opacity-70" />
      {labels[status]}
    </span>
  )
}
