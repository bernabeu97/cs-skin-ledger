import { describe, expect, it } from 'vitest'
import { windowModeSize } from './windowMode'

describe('windowModeSize', () => {
  it('keeps overlay smaller than the regular ticker windows', () => {
    expect(windowModeSize('overlay')).toEqual({ width: 340, height: 258 })
    expect(windowModeSize('compact')).toEqual({ width: 420, height: 620 })
    expect(windowModeSize('expanded')).toEqual({ width: 1020, height: 760 })
  })
})
