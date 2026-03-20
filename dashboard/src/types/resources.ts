export type ResourceStatus = 'healthy' | 'degraded' | 'error' | 'unknown'

export interface ResourceInfo {
  name:        string
  type:        'kinesis' | 'lambda' | 'dynamodb' | 'redis' | 'postgres'
  status:      ResourceStatus
  details:     Record<string, string | number>
  lastChecked: Date
}

export interface KinesisStreamInfo extends ResourceInfo {
  type: 'kinesis'
  details: {
    streamName:      string
    shardCount:      number
    retentionHours:  number
    sequenceNumber?: string
  }
}

export interface LambdaInfo extends ResourceInfo {
  type: 'lambda'
  details: {
    functionName:    string
    runtime:         string
    lastModified:    string
    lastInvocation?: string
    errorRate?:      number
  }
}

export interface DynamoInfo extends ResourceInfo {
  type: 'dynamodb'
  details: {
    tableName:  string
    itemCount:  number
    status:     string
  }
}

export interface RedisInfo extends ResourceInfo {
  type: 'redis'
  details: {
    usedMemory:        string
    connectedClients:  number
    totalKeys:         number
  }
}

export interface PostgresInfo extends ResourceInfo {
  type: 'postgres'
  details: {
    database:           string
    activeConnections:  number
    intervalCount:      number
  }
}
