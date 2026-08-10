<script setup lang="ts">
import { ref } from 'vue'
import type { ColumnOption } from '../utils/columnVisibility'

const props = defineProps<{
  columns: ColumnOption[]
  modelValue: string[]
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string[]): void }>()
const details = ref<HTMLDetailsElement | null>(null)

function toggle(key: string, checked: boolean) {
  const next = checked
    ? [...new Set([...props.modelValue, key])]
    : props.modelValue.filter(value => value !== key)
  if (next.length > 0) emit('update:modelValue', next)
}
</script>

<template>
  <details ref="details" class="column-picker" @keydown.esc="details && (details.open = false)">
    <summary class="btn btn-ghost btn-sm">
      显示字段
      <span class="column-count">{{ modelValue.length }}/{{ columns.length }}</span>
    </summary>
    <div class="column-menu" role="group" aria-label="选择列表显示字段">
      <div class="column-menu-title">显示字段</div>
      <label v-for="column in columns" :key="column.key" class="column-option">
        <input
          type="checkbox"
          :checked="modelValue.includes(column.key)"
          :disabled="modelValue.length === 1 && modelValue.includes(column.key)"
          @change="toggle(column.key, ($event.target as HTMLInputElement).checked)"
        />
        <span>{{ column.label }}</span>
      </label>
      <button type="button" class="column-reset" @click="emit('update:modelValue', columns.map(column => column.key))">
        全部显示
      </button>
    </div>
  </details>
</template>

<style scoped>
.column-picker { position: relative; }
.column-picker > summary { list-style: none; cursor: pointer; white-space: nowrap; }
.column-picker > summary::-webkit-details-marker { display: none; }
.column-count {
  min-width: 32px;
  margin-left: 4px;
  padding: 1px 5px;
  border-radius: 999px;
  background: var(--surface-muted);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 10px;
  text-align: center;
}
.column-menu {
  position: absolute;
  z-index: 50;
  top: calc(100% + 6px);
  right: 0;
  display: grid;
  min-width: 210px;
  max-height: min(420px, 70vh);
  padding: 8px;
  overflow-y: auto;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}
.column-menu-title { padding: 4px 7px 7px; color: var(--text-muted); font-size: 11px; font-weight: 650; }
.column-option {
  display: flex;
  min-height: 32px;
  align-items: center;
  gap: 8px;
  padding: 5px 7px;
  border-radius: 5px;
  cursor: pointer;
  font-size: 13px;
}
.column-option:hover { background: var(--surface-muted); }
.column-option:focus-within { outline: 2px solid var(--accent); outline-offset: -2px; }
.column-option input { accent-color: var(--accent); }
.column-option:has(input:disabled) { cursor: not-allowed; opacity: .55; }
.column-reset {
  margin-top: 6px;
  padding: 8px 7px 3px;
  border: 0;
  border-top: 1px solid var(--border);
  background: transparent;
  color: var(--accent);
  cursor: pointer;
  text-align: left;
  font-size: 12px;
}
@media (max-width: 560px) {
  .column-menu { position: fixed; top: 68px; right: 12px; left: 12px; max-height: calc(100vh - 84px); }
}
</style>
