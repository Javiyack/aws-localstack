import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts'
import type { DataPoint } from '@/types/simulation'

interface Props { data: DataPoint[] }

export function ThroughputChart({ data }: Props) {
  return (
    <div className="bg-white rounded-lg border p-4">
      <h4 className="text-sm font-medium text-gray-700 mb-3">Throughput (msg/s)</h4>
      <ResponsiveContainer width="100%" height={200}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
          <XAxis dataKey="label" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
          <YAxis tick={{ fontSize: 10 }} />
          <Tooltip
            contentStyle={{ fontSize: 12 }}
            formatter={(v: number) => [`${v} msg/s`, 'Rate']}
          />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#2563eb"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
