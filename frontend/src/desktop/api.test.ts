import { describe, expect, it } from 'vitest'
import { normalizeBase } from './api'

describe('normalizeBase', () => {
  it('uses the server origin when a page URL is pasted', () => {
    expect(normalizeBase('http://47.108.166.67/trades?q=ak#top'))
      .toBe('http://47.108.166.67')
  })

  it('keeps a local API origin unchanged', () => {
    expect(normalizeBase(' http://localhost:8080/ '))
      .toBe('http://localhost:8080')
  })
})
