import { describe, expect, it } from 'vitest'
import { errorMessage } from './client'

describe('errorMessage', () => {
  it('explains an oversized import instead of exposing HTTP 413', () => {
    expect(errorMessage({ isAxiosError: true, response: { status: 413 } }))
      .toBe('导入文件超过 64MB 上限，请缩小文件后重试')
  })
})
