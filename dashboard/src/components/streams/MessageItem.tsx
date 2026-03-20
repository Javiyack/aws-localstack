import type { StreamRecord } from '@/api/kinesis'

interface Props { record: StreamRecord }

export function MessageItem({ record }: Props) {
  const ts = new Date(record.approximateArrivalTimestamp * 1000).toLocaleTimeString()
  return (
    <div className="border border-gray-100 rounded p-3 bg-white hover:bg-gray-50 transition-colors">
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-mono text-gray-400">
          #{record.sequenceNumber.slice(-8)}
        </span>
        <span className="text-xs text-gray-400">{ts}</span>
      </div>
      <pre className="text-xs font-mono text-gray-700 whitespace-pre-wrap break-all overflow-auto max-h-40">
        {typeof record.data === 'string'
          ? record.data
          : JSON.stringify(record.data, null, 2)}
      </pre>
    </div>
  )
}
