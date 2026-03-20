import type { StreamRecord } from '@/api/kinesis'
import { MessageItem } from './MessageItem'

interface Props { records: StreamRecord[] }

export function MessageList({ records }: Props) {
  if (records.length === 0) {
    return (
      <div className="text-center py-12 text-gray-400 text-sm">
        Sin mensajes. Inicia la lectura del stream.
      </div>
    )
  }
  return (
    <div className="space-y-2 max-h-[600px] overflow-y-auto pr-1">
      {records.map(r => (
        <MessageItem key={r.sequenceNumber} record={r} />
      ))}
    </div>
  )
}
