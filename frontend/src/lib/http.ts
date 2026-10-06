import axios from 'axios'

export interface ServiceHealth {
  status: 'UP'
  service: 'backend-java' | 'ai-service'
  version: string
}

export function parseHealthResponse(data: unknown, service: ServiceHealth['service']): ServiceHealth {
  if (typeof data !== 'object' || data === null) throw new Error('健康检查返回格式不正确')
  const result = data as Record<string, unknown>
  if (result.status !== 'UP' || result.service !== service || typeof result.version !== 'string') {
    throw new Error('服务身份或健康状态不符合预期')
  }
  return { status: 'UP', service, version: result.version }
}

export async function readHealth(service: ServiceHealth['service']): Promise<ServiceHealth> {
  const path = service === 'backend-java' ? '/api/health' : '/ai/health'
  const response = await axios.get<unknown>(path, { timeout: 5000 })
  return parseHealthResponse(response.data, service)
}

