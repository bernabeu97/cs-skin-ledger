import { ref, watch } from 'vue'

export interface ColumnOption {
  key: string
  label: string
  defaultVisible?: boolean
}

export function useColumnVisibility(storageKey: string, columns: ColumnOption[]) {
  const allowed = new Set(columns.map(column => column.key))
  const defaults = columns.filter(column => column.defaultVisible !== false).map(column => column.key)
  let initial = defaults

  try {
    const stored = JSON.parse(localStorage.getItem(storageKey) ?? 'null')
    const valid = Array.isArray(stored) ? stored.filter(value => typeof value === 'string' && allowed.has(value)) : []
    if (valid.length > 0) initial = [...new Set(valid)]
  } catch {
    // 无效的本地偏好直接回退到默认列。
  }

  const visibleColumns = ref<string[]>(initial)
  watch(visibleColumns, value => localStorage.setItem(storageKey, JSON.stringify(value)), { deep: true })

  return {
    visibleColumns,
    isColumnVisible: (key: string) => visibleColumns.value.includes(key)
  }
}
