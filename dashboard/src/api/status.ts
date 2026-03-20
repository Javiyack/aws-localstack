import { localstackClient } from './client'
import type { ResourceInfo } from '@/types/resources'

export async function fetchAllResourceStatuses(): Promise<ResourceInfo[]> {
  const settled = await Promise.allSettled([
    fetchKinesisStatus('input-stream'),
    fetchKinesisStatus('output-stream'),
    fetchDynamoStatus('audit-records'),
    fetchLambdaStatus('pipeline-processor')
  ])
  return settled.flatMap(r => (r.status === 'fulfilled' ? [r.value] : []))
}

async function fetchKinesisStatus(streamName: string): Promise<ResourceInfo> {
  try {
    const res = await localstackClient.post(
      '/kinesis',
      { StreamName: streamName },
      { headers: { 'X-Amz-Target': 'Kinesis_20131202.DescribeStreamSummary' } }
    )
    const info = res.data.StreamDescriptionSummary
    return {
      name:   streamName,
      type:   'kinesis',
      status: info.StreamStatus === 'ACTIVE' ? 'healthy' : 'degraded',
      details: {
        streamName,
        shardCount:      info.OpenShardCount,
        retentionHours:  info.RetentionPeriodHours
      },
      lastChecked: new Date()
    }
  } catch {
    return { name: streamName, type: 'kinesis', status: 'error', details: { streamName }, lastChecked: new Date() }
  }
}

async function fetchDynamoStatus(tableName: string): Promise<ResourceInfo> {
  try {
    const res = await localstackClient.post(
      '/dynamodb',
      { TableName: tableName },
      { headers: { 'X-Amz-Target': 'DynamoDB_20120810.DescribeTable' } }
    )
    const t = res.data.Table
    return {
      name:   tableName,
      type:   'dynamodb',
      status: t.TableStatus === 'ACTIVE' ? 'healthy' : 'degraded',
      details: { tableName, itemCount: t.ItemCount ?? 0, status: t.TableStatus },
      lastChecked: new Date()
    }
  } catch {
    return {
      name: tableName, type: 'dynamodb', status: 'error',
      details: { tableName, itemCount: 0, status: 'error' }, lastChecked: new Date()
    }
  }
}

async function fetchLambdaStatus(functionName: string): Promise<ResourceInfo> {
  try {
    const res = await localstackClient.get(`/2015-03-31/functions/${functionName}`)
    const fn  = res.data
    return {
      name:   functionName,
      type:   'lambda',
      status: fn.State === 'Active' ? 'healthy' : 'degraded',
      details: { functionName, runtime: fn.Runtime, lastModified: fn.LastModified },
      lastChecked: new Date()
    }
  } catch {
    return {
      name: functionName, type: 'lambda', status: 'error',
      details: { functionName, runtime: 'java21', lastModified: '' }, lastChecked: new Date()
    }
  }
}
