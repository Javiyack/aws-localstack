import axios from 'axios'

/** Cliente apuntando a LocalStack (para llamadas directas a servicios AWS emulados). */
export const localstackClient = axios.create({
  baseURL: 'http://localhost:4566',
  headers: {
    'x-amz-content-sha256': 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
    'Authorization':
      'AWS4-HMAC-SHA256 Credential=test/20260101/us-east-1/kinesis/aws4_request, ' +
      'SignedHeaders=host, Signature=test'
  }
})

/** Cliente genérico para APIs REST propias (ej. value-backend). */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:3000'
})
