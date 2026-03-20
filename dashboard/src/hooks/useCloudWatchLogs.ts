import { useState, useCallback } from 'react'
import {
  listLogGroups, listLogStreams, getLogEvents, filterByLevel
} from '@/api/cloudwatch'
import type { LogEvent, LogLevel } from '@/api/cloudwatch'

export function useCloudWatchLogs() {
  const [logGroups, setLogGroups]       = useState<string[]>([])
  const [logStreams, setLogStreams]      = useState<string[]>([])
  const [events, setEvents]             = useState<LogEvent[]>([])
  const [selectedGroup, setSelectedGroup]   = useState('')
  const [selectedStream, setSelectedStream] = useState('')
  const [level, setLevel]               = useState<LogLevel>('ALL')
  const [loading, setLoading]           = useState(false)
  const [error, setError]               = useState<string | null>(null)

  const loadGroups = useCallback(async () => {
    try {
      const groups = await listLogGroups()
      setLogGroups(groups.map(g => g.logGroupName))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error cargando grupos')
    }
  }, [])

  const loadStream = useCallback(async (groupName: string, streamName: string) => {
    setSelectedStream(streamName)
    setLoading(true)
    setError(null)
    try {
      setEvents(await getLogEvents(groupName, streamName))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error cargando logs')
    } finally {
      setLoading(false)
    }
  }, [])

  const selectGroup = useCallback(async (groupName: string) => {
    setSelectedGroup(groupName)
    setSelectedStream('')
    setEvents([])
    const streams = await listLogStreams(groupName)
    setLogStreams(streams)
    if (streams.length > 0) loadStream(groupName, streams[0])
  }, [loadStream])

  const selectStream = useCallback(
    (streamName: string) => loadStream(selectedGroup, streamName),
    [selectedGroup, loadStream]
  )

  const refresh = useCallback(() => {
    if (selectedGroup && selectedStream) loadStream(selectedGroup, selectedStream)
  }, [selectedGroup, selectedStream, loadStream])

  return {
    logGroups, logStreams,
    events:       filterByLevel(events, level),
    selectedGroup, selectedStream, level,
    loading, error,
    loadGroups, selectGroup, selectStream, setLevel, refresh
  }
}
