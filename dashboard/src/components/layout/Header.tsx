interface Props { title: string; subtitle?: string }

export function Header({ title, subtitle }: Props) {
  return (
    <div className="border-b border-gray-200 pb-4 mb-6">
      <h2 className="text-xl font-semibold text-gray-900">{title}</h2>
      {subtitle && <p className="text-sm text-gray-500 mt-0.5">{subtitle}</p>}
    </div>
  )
}
