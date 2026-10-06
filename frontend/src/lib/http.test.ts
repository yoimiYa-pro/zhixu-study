import { describe, expect, it } from 'vitest'
import { parseHealthResponse } from './http'

describe('health response boundary', () => {
  it('rejects wrong services and unavailable states', () => {
    expect(() => parseHealthResponse({ status: 'UP', service: 'ai-service', version: '1' }, 'backend-java')).toThrow()
    expect(() => parseHealthResponse({ status: 'DOWN', service: 'backend-java', version: '1' }, 'backend-java')).toThrow()
  })
  it.each([null, '<html>proxy failure</html>', {}, { status: 'UP' }])('rejects malformed proxy responses: %s', data => {
    expect(() => parseHealthResponse(data, 'backend-java')).toThrow()
  })
})
