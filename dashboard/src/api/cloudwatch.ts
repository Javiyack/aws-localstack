import { localstackClient } from './client'

export interface LogGroup {
  logGroupName:    string
  creationTime:    number
  retentionInDays?: number
  storedBytes:     number
}

export interface LogEvent {
  timestamp:     number
  message:       string
  ingestionTime: number
}

export type LogLevel = 'ALL' | 'ERROR' | 'WARN' | 'INFO' | 'DEBUG'

export async function listLogGroups(prefix = '/aws/lambda'): Promise<LogGroup[]> {
  try {
    const res = await localstackClient.post(
      '/logs',
      { logGroupNamePrefix: prefix, limit: 50 },
      { headers: { 'X-Amz-Target': 'Logs_20140328.DescribeLogGroups' } }
    )
    return (res.data.logGroups ?? []) as LogGroup[]
  } catch {
    return []
  }
}

export async function listLogStreams(logGroupName: string): Promise<string[]> {
  try {
    const res = await localstackClient.post(
      '/logs',
      { logGroupName, limit: 20, descending: true },
      { headers: { 'X-Amz-Target': 'Logs_20140328.DescribeLogStreams' } }
    )
    return ((res.data.logStreams ?? []) as Array<{ logStreamName: string }>).map(
      s => s.logStreamName
    )
  } catch {
    return []
  }
}

export async function getLogEvents(
  logGroupName: string,
  logStreamName: string,
  limit = 100
): Promise<LogEvent[]> {
  try {
    const res = await localstackClient.post(
      '/logs',
      { logGroupName, logStreamName, limit, startFromHead: false },
      { headers: { 'X-Amz-Target': 'Logs_20140328.GetLogEvents' } }
    )
    return (res.data.events ?? []) as LogEvent[]
  } catch {
    return []
  }
}

export function filterByLevel(events: LogEvent[], level: LogLevel): LogEvent[] {
  if (level === 'ALL') return events
  return events.filter(
    e =>
      e.message.toUpperCase().includes(`[${level}]`) ||
      e.message.toUpperCase().includes(level)
  )
}
