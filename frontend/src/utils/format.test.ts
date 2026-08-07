import { describe, expect, it } from 'vitest'
import { formatMoney, toIsoLocal } from './format'

describe('format', () => {
  it('formats money with thousand separators', () => {
    expect(formatMoney(1234.5)).toBe('1,234.50')
  })

  it('handles null as dash', () => {
    expect(formatMoney(null)).toBe('-')
  })

  it('formats local datetime to ISO string', () => {
    const d = new Date(2026, 0, 5, 9, 7, 8)
    expect(toIsoLocal(d)).toBe('2026-01-05T09:07:08')
  })
})