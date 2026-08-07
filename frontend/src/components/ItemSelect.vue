<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { searchItems } from '../api/client'
import type { Item } from '../types'

const props = defineProps<{ modelValue: Item | null; placeholder?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: Item | null): void }>()

const text = ref(props.modelValue?.nameZh ?? props.modelValue?.marketHashName ?? '')
const open = ref(false)
const loading = ref(false)
const results = ref<Item[]>([])
const highlight = ref(0)
let timer: number | undefined
let ignoreBlur: boolean = false

watch(() => props.modelValue, (v) => {
  if (v) text.value = v.nameZh ?? v.marketHashName
})

async function doSearch(q: string) {
  if (!q.trim()) {
    results.value = []
    open.value = false
    return
  }
  loading.value = true
  try {
    results.value = await searchItems(q.trim(), 50)
    highlight.value = 0
    open.value = true
  } finally {
    loading.value = false
  }
}

function onInput() {
  emit('update:modelValue', null)
  window.clearTimeout(timer)
  timer = window.setTimeout(() => doSearch(text.value), 250)
}

function select(item: Item) {
  emit('update:modelValue', item)
  text.value = item.nameZh ?? item.marketHashName
  open.value = false
}

function onKey(e: KeyboardEvent) {
  if (!open.value && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
    open.value = true
    return
  }
  if (e.key === 'ArrowDown') {
    highlight.value = Math.min(highlight.value + 1, Math.max(results.value.length - 1, 0))
    e.preventDefault()
  } else if (e.key === 'ArrowUp') {
    highlight.value = Math.max(highlight.value - 1, 0)
    e.preventDefault()
  } else if (e.key === 'Enter') {
    const item = results.value[highlight.value]
    if (open.value && item) {
      select(item)
      e.preventDefault()
    }
  } else if (e.key === 'Escape') {
    open.value = false
  }
}

function onFocus() {
  if (text.value.trim()) doSearch(text.value)
}

function onMouseDown() { ignoreBlur = true }

function onMouseUp() { ignoreBlur = false }

function onBlur() {
  window.setTimeout(() => {
    if (!ignoreBlur) open.value = false
  }, 120)
}

onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<template>
  <div class="item-select" @mousedown="onMouseDown" @mouseup="onMouseUp">
    <input
      v-model="text"
      class="input"
      :placeholder="placeholder ?? '输入关键词搜索饰品（支持中文）'"
      @input="onInput"
      @keydown="onKey"
      @focus="onFocus"
      @blur="onBlur"
    />
    <div v-if="open" class="dropdown">
      <div v-if="loading" class="hint">搜索中…</div>
      <div v-else-if="results.length === 0" class="hint">无匹配结果，可继续输入自定义名称</div>
      <ul v-else>
        <li
          v-for="(it, i) in results"
          :key="it.id"
          :class="{ active: i === highlight }"
          @mousedown.prevent="select(it)"
        >
          <span class="name">{{ it.nameZh }}</span>
          <span class="meta">
            {{ [it.weapon, it.category, it.minFloat != null ? `磨损 ${it.minFloat}-${it.maxFloat}` : ''].filter(Boolean).join(' · ') }}
          </span>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.item-select { position: relative; }
.dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0; z-index: 30;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius);
  box-shadow: var(--shadow-lg); max-height: 280px; overflow: auto;
}
.hint { padding: 10px 12px; font-size: 12px; color: var(--text-muted); }
ul { list-style: none; margin: 0; padding: 4px; }
li {
  display: flex; flex-direction: column; gap: 2px; padding: 7px 10px; border-radius: var(--radius-sm);
  cursor: pointer;
}
li:hover, li.active { background: var(--accent-soft); }
.name { font-size: 13px; color: var(--text); }
.meta { font-size: 11px; color: var(--text-muted); }
</style>