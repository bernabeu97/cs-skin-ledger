import { describe, expect, it } from 'vitest'
import { formatDateTime, formatMoney, formatQty, formatSignedMoney, toIsoLocal } from './format'

describe('format', () => {
  it('formats money with thousand separators', () => {
    expect(formatMoney(1234.5)).toBe('1,234.50')
  })

  it('handles null as dash', () => {
    expect(formatMoney(null)).toBe('-')
  })

  it('formats signed money', () => {
    expect(formatSignedMoney(120.5)).toBe('+120.50')
    expect(formatSignedMoney(-8)).toBe('-8.00')
  })

  it('formats quantity without trailing zeros', () => {
    expect(formatQty(2.5)).toBe('2.5')
    expect(formatQty(0.0001)).toBe('0.0001')
  })

  it('formats datetime to local string', () => {
    expect(formatDateTime('2026-01-05T10:00:00')).toBe('2026-01-05 10:00')
  })

  it('formats local datetime to ISO string', () => {
    const d = new Date(2026, 0, 5, 9, 7, 8)
    expect(toIsoLocal(d)).toBe('2026-01-05T09:07:08')
  })
})