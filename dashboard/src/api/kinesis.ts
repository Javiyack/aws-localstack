import { localstackClient } from './client'
import type { InputMessage } from '@/types/messages'

export type AnyStreamMessage = InputMessage | Record<string, unknown>

export interface StreamRecord {
  sequenceNumber:              string
  approximateArrivalTimestamp: number
  data:                        AnyStreamMessage
  raw:                         string
}

export type StreamName = 'input-stream' | 'output-stream'

export async function getShardIterator(streamName: StreamName): Promise<string> {
  const res = await localstackClient.post(
    '/kinesis',
    { StreamName: streamName, ShardId: 'shardId-000000000000', ShardIteratorType: 'LATEST' },
    { headers: { 'X-Amz-Target': 'Kinesis_20131202.GetShardIterator' } }
  )
  return res.data.ShardIterator as string
}

export async function getRecords(
  iterator: string,
  limit = 100
): Promise<{ records: StreamRecord[]; nextIterator: string | null }> {
  const res = await localstackClient.post(
    '/kinesis',
    { ShardIterator: iterator, Limit: limit },
    { headers: { 'X-Amz-Target': 'Kinesis_20131202.GetRecords' } }
  )
  const records: StreamRecord[] = (
    res.data.Records as Array<{
      SequenceNumber:              string
      ApproximateArrivalTimestamp: number
      Data:                        string
    }>
  ).map(r => {
    const raw = atob(r.Data)
    let data: AnyStreamMessage
    try { data = JSON.parse(raw) } catch { data = { raw } }
    return {
      sequenceNumber:              r.SequenceNumber,
      approximateArrivalTimestamp: r.ApproximateArrivalTimestamp,
      data,
      raw
    }
  })
  return { records, nextIterator: (res.data.NextShardIterator as string | null) ?? null }
}

export async function putRecord(streamName: StreamName, message: InputMessage): Promise<void> {
  await localstackClient.post(
    '/kinesis',
    {
      StreamName:   streamName,
      Data:         btoa(JSON.stringify(message)),
      PartitionKey: message.nodeId ?? 'default'
    },
    { headers: { 'X-Amz-Target': 'Kinesis_20131202.PutRecord' } }
  )
}
