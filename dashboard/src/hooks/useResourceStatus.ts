import { useState, useCallback } from 'react'
import { usePolling } from './usePolling'
import type { ResourceInfo } from '@/types/resources'
import { fetchAllResourceStatuses } from '@/api/status'

export function useResourceStatus(intervalMs = 10_000) {
  const [resources, setResources] = useState<ResourceInfo[]>([])
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchAllResourceStatuses()
      setResources(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error desconocido')
    } finally {
      setLoading(false)
    }
  }, [])

  usePolling(refresh, intervalMs)

  return { resources, loading, error, refresh }
}
