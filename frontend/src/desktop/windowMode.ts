export type WindowMode = 'overlay' | 'compact' | 'expanded'

const WINDOW_SIZES = {
  overlay: { width: 340, height: 258 },
  compact: { width: 420, height: 620 },
  expanded: { width: 1020, height: 760 }
} satisfies Record<WindowMode, { width: number; height: number }>

export function windowModeSize(mode: WindowMode) {
  return WINDOW_SIZES[mode]
}
