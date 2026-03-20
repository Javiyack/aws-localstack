import { localstackClient } from './client'

export interface DlqInfo {
  queueName:              string
  approximateMessages:    number
  approximateInFlight:    number
  approximateDelayed:     number
}

export async function getDlqInfo(queueName = 'aws-local-pipeline-dlq'): Promise<DlqInfo> {
  try {
    // 1. Obtener URL de la cola
    const urlRes = await localstackClient.post(
      '/sqs',
      `Action=GetQueueUrl&QueueName=${encodeURIComponent(queueName)}&Version=2012-11-05`,
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    )
    const queueUrl = (urlRes.data as string)
      .match(/<QueueUrl>(.*?)<\/QueueUrl>/)?.[1] ?? ''

    if (!queueUrl) throw new Error('Cola no encontrada')

    // 2. Obtener atributos
    const attrRes = await localstackClient.post(
      '/sqs',
      `Action=GetQueueAttributes` +
      `&QueueUrl=${encodeURIComponent(queueUrl)}` +
      `&AttributeName.1=ApproximateNumberOfMessages` +
      `&AttributeName.2=ApproximateNumberOfMessagesNotVisible` +
      `&AttributeName.3=ApproximateNumberOfMessagesDelayed` +
      `&Version=2012-11-05`,
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
    )

    const xml = attrRes.data as string
    const extract = (name: string): number => {
      const match = xml.match(new RegExp(`<Name>${name}</Name>\\s*<Value>(\\d+)</Value>`))
      return match ? parseInt(match[1], 10) : 0
    }

    return {
      queueName,
      approximateMessages:  extract('ApproximateNumberOfMessages'),
      approximateInFlight:  extract('ApproximateNumberOfMessagesNotVisible'),
      approximateDelayed:   extract('ApproximateNumberOfMessagesDelayed')
    }
  } catch {
    return { queueName, approximateMessages: 0, approximateInFlight: 0, approximateDelayed: 0 }
  }
}
