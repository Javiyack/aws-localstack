import { Component, type ReactNode } from 'react'

interface Props  { children: ReactNode; fallback?: ReactNode }
interface State  { hasError: boolean; error?: Error }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error }
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback ?? (
        <div className="p-4 bg-red-50 border border-red-200 rounded-lg">
          <h3 className="text-red-800 font-semibold mb-1">Error inesperado</h3>
          <p className="text-red-600 text-sm font-mono">{this.state.error?.message}</p>
        </div>
      )
    }
    return this.props.children
  }
}
