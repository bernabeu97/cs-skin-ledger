import { describe, expect, it } from 'vitest'
import { isSecureServer, normalizeBase } from './api'

describe('normalizeBase', () => {
  it('uses the server origin when a page URL is pasted', () => {
    expect(normalizeBase('http://47.108.166.67/trades?q=ak#top'))
      .toBe('http://47.108.166.67')
  })

  it('keeps a local API origin unchanged', () => {
    expect(normalizeBase(' http://localhost:8080/ '))
      .toBe('http://localhost:8080')
  })

  it('adds http:// when only a bare host or IP is entered', () => {
    expect(normalizeBase('47.108.166.67')).toBe('http://47.108.166.67')
    expect(normalizeBase('localhost:8080')).toBe('http://localhost:8080')
  })
})

describe('isSecureServer', () => {
  it('accepts HTTPS and local development servers', () => {
    expect(isSecureServer('https://example.com')).toBe(true)
    expect(isSecureServer('http://localhost:8080')).toBe(true)
  })

  it('warns for a public HTTP server without blocking credential storage', () => {
    expect(isSecureServer('http://47.108.166.67')).toBe(false)
  })
})
