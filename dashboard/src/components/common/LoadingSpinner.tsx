interface Props { size?: 'sm' | 'md' | 'lg' }

const sizes = { sm: 'w-4 h-4', md: 'w-8 h-8', lg: 'w-12 h-12' } as const

export function LoadingSpinner({ size = 'md' }: Props) {
  return (
    <div className="flex items-center justify-center p-4">
      <div
        className={`${sizes[size]} border-4 border-gray-200 border-t-blue-600 rounded-full animate-spin`}
        role="status"
        aria-label="Cargando"
      />
    </div>
  )
}
