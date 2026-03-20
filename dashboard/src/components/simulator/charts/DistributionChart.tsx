import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'

interface Props { data: { name: string; value: number }[] }

const COLORS = ['#2563eb', '#16a34a']

export function DistributionChart({ data }: Props) {
  const total = data.reduce((s, d) => s + d.value, 0)
  return (
    <div className="bg-white rounded-lg border p-4">
      <h4 className="text-sm font-medium text-gray-700 mb-3">
        Distribución ({total.toLocaleString()} total)
      </h4>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            cx="50%" cy="50%"
            outerRadius={70}
            isAnimationActive={false}
          >
            {data.map((_, i) => (
              <Cell key={i} fill={COLORS[i % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            formatter={(v: number) => [v.toLocaleString(), '']}
            contentStyle={{ fontSize: 12 }}
          />
          <Legend wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}
