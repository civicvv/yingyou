import { describe, expect, it } from 'vitest'
import { newRequestId } from './requestId'

describe('newRequestId', () => {
  it('生成符合 UUID v4 格式的请求号', () => {
    const id = newRequestId()
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
  })

  it('连续调用不会返回相同请求号', () => {
    expect(newRequestId()).not.toBe(newRequestId())
  })
})
