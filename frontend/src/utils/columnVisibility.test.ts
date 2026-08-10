import { nextTick } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'
import { useColumnVisibility } from './columnVisibility'

describe('useColumnVisibility', () => {
  beforeEach(() => localStorage.clear())

  it('restores valid columns and persists later changes', async () => {
    localStorage.setItem('columns:test', JSON.stringify(['name', 'missing']))
    const { visibleColumns, isColumnVisible } = useColumnVisibility('columns:test', [
      { key: 'name', label: '名称' },
      { key: 'price', label: '价格' }
    ])

    expect(visibleColumns.value).toEqual(['name'])
    expect(isColumnVisible('price')).toBe(false)

    visibleColumns.value = ['name', 'price']
    await nextTick()
    expect(localStorage.getItem('columns:test')).toBe('["name","price"]')
  })
})
