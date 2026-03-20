import type { ResourceInfo } from '@/types/resources'
import { StatusBadge } from './StatusBadge'
import { Activity, Database, Layers, Server, GitBranch } from 'lucide-react'

type IconComponent = React.ComponentType<{ size?: number; className?: string }>

const ICONS: Record<ResourceInfo['type'], IconComponent> = {
  kinesis:  GitBranch,
  lambda:   Layers,
  dynamodb: Database,
  redis:    Server,
  postgres: Activity
}

interface Props { resource: ResourceInfo; onClick?: () => void }

export function ResourceCard({ resource, onClick }: Props) {
  const Icon = ICONS[resource.type]
  return (
    <div
      className="bg-white rounded-lg border border-gray-200 p-4 cursor-pointer hover:shadow-md transition-shadow"
      onClick={onClick}
    >
      <div className="flex justify-between items-start mb-3">
        <div className="flex items-center gap-2">
          <Icon size={18} className="text-gray-500" />
          <span className="font-medium text-gray-900 text-sm">{resource.name}</span>
        </div>
        <StatusBadge status={resource.status} />
      </div>

      <div className="space-y-1">
        {Object.entries(resource.details).map(([k, v]) => (
          <div key={k} className="flex justify-between text-xs text-gray-500">
            <span className="capitalize">{k.replace(/([A-Z])/g, ' $1').toLowerCase()}</span>
            <span className="font-mono text-gray-700">{String(v)}</span>
          </div>
        ))}
      </div>

      <p className="text-xs text-gray-400 mt-3">
        Actualizado: {resource.lastChecked.toLocaleTimeString()}
      </p>
    </div>
  )
}
