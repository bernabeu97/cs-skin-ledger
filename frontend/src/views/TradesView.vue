<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import TradeForm from '../components/TradeForm.vue'
import TradeTable from '../components/TradeTable.vue'
import { errorMessage } from '../api/client'
import { useTradesStore } from '../stores/trades'
import { useUiStore } from '../stores/ui'
import { downloadBlob } from '../utils/format'
import type { Trade, TradeCreateRequest } from '../types'

type SortKey = 'tradedAt' | 'quantity' | 'unitPrice' | 'totalAmount'

const store = useTradesStore()
const ui = useUiStore()

const q = ref('')
const platform = ref('')
const direction = ref('')
const range = ref<'7' | '30' | 'all' | 'custom'>('all')
const fromDate = ref('')
const toDate = ref('')
const sortKey = ref<SortKey>('tradedAt')
const sortDir = ref<'asc' | 'desc'>('desc')
const showForm = ref(false)
const editing = ref<Trade | null>(null)
const saving = ref(false)
const confirmTarget = ref<Trade | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const searchEl = ref<HTMLInputElement | null>(null)

const hasFilters = computed(() => {
  const customActive = range.value === 'custom' && (!!fromDate.value || !!toDate.value)
  return !!q.value || !!platform.value || !!direction.value || range.value === '7' || range.value === '30' || customActive
})

const sortedTrades = computed(() => {
  const arr = [...store.trades]
  const dir = sortDir.value === 'asc' ? 1 : -1
  arr.sort((a, b) => {
    if (sortKey.value === 'tradedAt') return a.tradedAt.localeCompare(b.tradedAt) * dir
    return (Number(a[sortKey.value]) - Number(b[sortKey.value])) * dir
  })
  return arr
})

function buildQuery(): Record<string, string> {
  const params: Record<string, string> = {}
  if (q.value) params.q = q.value
  if (platform.value) params.platform = platform.value
  if (direction.value) params.direction = direction.value
  if (range.value === '7' || range.value === '30') {
    const days = Number(range.value)
    const from = new Date()
    from.setDate(from.getDate() - days)
    const today = new Date()
    params.from = `${from.toISOString().slice(0, 10)}T00:00:00`
    params.to = `${today.toISOString().slice(0, 10)}T23:59:59`
  } else if (range.value === 'custom') {
    if (fromDate.value) params.from = `${fromDate.value}T00:00:00`
    if (toDate.value) params.to = `${toDate.value}T23:59:59`
  }
  return params
}

async function applyFilters() {
  await store.loadTrades(buildQuery())
}

function resetFilters() {
  q.value = ''
  platform.value = ''
  direction.value = ''
  range.value = 'all'
  fromDate.value = ''
  toDate.value = ''
  applyFilters()
}

function toggleSort(key: SortKey) {
  if (sortKey.value === key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDir.value = 'desc'
  }
}

function openCreate() {
  editing.value = null
  showForm.value = true
}

function openEdit(t: Trade) {
  editing.value = t
  showForm.value = true
}

async function onSaved(payload: TradeCreateRequest) {
  saving.value = true
  try {
    if (editing.value) {
      await store.updateTrade(editing.value.id, payload)
      ui.toast('success', '交易已更新')
    } else {
      await store.createTrade(payload)
      ui.toast('success', '交易已保存')
    }
    showForm.value = false
    await applyFilters()
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    saving.value = false
  }
}

function requestDelete(t: Trade) {
  confirmTarget.value = t
}

async function onConfirmDelete() {
  const t = confirmTarget.value
  confirmTarget.value = null
  if (!t) return
  try {
    await store.deleteTrade(t.id)
    ui.toast('success', '交易已删除')
    await applyFilters()
  } catch (e) {
    ui.toast('error', errorMessage(e))
  }
}

async function onImport() {
  const file = fileInput.value?.files?.[0]
  if (!file) return
  try {
    const result = await store.importCsv(file)
    if (result.failed === 0) {
      ui.toast('success', `导入成功 ${result.created} 条`)
    } else {
      ui.toast('success', `导入完成：成功 ${result.created} 条，失败 ${result.failed} 条`)
      ui.toast('error', result.errors.slice(0, 3).join('\n') || '部分数据导入失败')
    }
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    if (fileInput.value) fileInput.value.value = ''
    await applyFilters()
  }
}

async function onExport(format: 'csv' | 'json' | 'xlsx') {
  try {
    await store.exportTrades(format)
    ui.toast('info', `已导出 ${format.toUpperCase()} 文件`)
  } catch (e) {
    ui.toast('error', errorMessage(e))
  }
}

function downloadCsvTemplate() {
  const header = 'itemName,platform,direction,quantity,unitPrice,fee,feeRate,currency,tradedAt,externalTradeId,status,note'
  downloadBlob(new Blob([header + '\n'], { type: 'text/csv' }), 'trades_template.csv')
}

function onGlobalKey(e: KeyboardEvent) {
  const tag = (e.target as HTMLElement | null)?.tagName
  if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return
  if (e.key === 'n' || e.key === 'N') {
    openCreate()
    e.preventDefault()
  } else if (e.key === '/') {
    searchEl.value?.focus()
    e.preventDefault()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKey)
  applyFilters()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKey))
</script>

<template>
  <div>
    <h1>
      交易记录
      <span v-if="!store.loading" class="count">{{ store.trades.length }} 笔</span>
      <span class="hint">按 <kbd>N</kbd> 新增 · 按 <kbd>/</kbd> 搜索</span>
    </h1>

    <div class="toolbar">
      <input ref="searchEl" v-model="q" class="input search" placeholder="搜索饰品（中/英文）" @keyup.enter="applyFilters" />
      <select v-model="platform" class="select filter" @change="applyFilters">
        <option value="">全部平台</option>
        <option value="steam">Steam</option>
        <option value="uu">UU</option>
        <option value="buff">BUFF</option>
      </select>
      <select v-model="direction" class="select filter" @change="applyFilters">
        <option value="">全部方向</option>
        <option value="BUY">买入</option>
        <option value="SELL">卖出</option>
      </select>

      <div class="range-group">
        <button
          v-for="r in [{v:'all',t:'全部'},{v:'7',t:'近7天'},{v:'30',t:'近30天'},{v:'custom',t:'自定义'}] as const"
          :key="r.v"
          type="button"
          class="chip"
          :class="{ active: range === r.v }"
          @click="range = r.v; applyFilters()"
        >{{ r.t }}</button>
        <template v-if="range === 'custom'">
          <input v-model="fromDate" class="input date" type="date" @change="applyFilters" />
          <span class="date-sep">至</span>
          <input v-model="toDate" class="input date" type="date" @change="applyFilters" />
        </template>
      </div>

      <div class="spacer"></div>

      <button type="button" class="btn btn-ghost btn-sm" title="下载 CSV 导入模板" @click="downloadCsvTemplate">模板</button>
      <label class="btn btn-sm">
        导入 CSV
        <input ref="fileInput" type="file" accept=".csv" style="display:none" @change="onImport" />
      </label>
      <div class="export-group">
        <button type="button" class="btn btn-ghost btn-sm" title="导出 CSV" @click="onExport('csv')">CSV</button>
        <button type="button" class="btn btn-ghost btn-sm" title="导出 JSON" @click="onExport('json')">JSON</button>
        <button type="button" class="btn btn-ghost btn-sm" title="导出 Excel" @click="onExport('xlsx')">Excel</button>
      </div>
      <button type="button" class="btn btn-primary" @click="openCreate">＋ 新增交易</button>
    </div>

    <div v-if="hasFilters" class="filter-summary">
      当前筛选：{{ q ? `关键词“${q}”` : '' }}{{ platform ? ` · ${platform}` : '' }}{{ direction === 'BUY' ? ' · 买入' : direction === 'SELL' ? ' · 卖出' : '' }}{{ range === '7' ? ' · 近7天' : range === '30' ? ' · 近30天' : range === 'custom' ? ' · 自定义区间' : '' }}
      <button type="button" class="btn btn-ghost btn-sm" @click="resetFilters">清除</button>
    </div>

    <div v-if="store.error" class="error-banner">
      <span>{{ store.error }}</span>
      <button type="button" class="btn btn-sm" @click="applyFilters">重试</button>
    </div>

    <TradeTable
      :trades="sortedTrades"
      :loading="store.loading"
      :sort-key="sortKey"
      :sort-dir="sortDir"
      @edit="openEdit"
      @delete="requestDelete"
      @sort="toggleSort"
    />

    <div v-if="!store.loading && !store.error && store.trades.length === 0" class="empty-state">
      <div class="empty-icon" aria-hidden="true">{{ hasFilters ? '🔍' : '📦' }}</div>
      <p v-if="hasFilters">没有符合当前筛选条件的交易，试试调整或清除筛选。</p>
      <p v-else>还没有交易记录，先录入一笔买入或卖出，盈亏统计会自动生成。</p>
      <button v-if="hasFilters" type="button" class="btn" @click="resetFilters">清除筛选</button>
      <button v-else type="button" class="btn btn-primary" @click="openCreate">新增第一笔交易</button>
    </div>

    <TradeForm v-if="showForm" :editing="editing" :saving="saving" @close="showForm = false" @saved="onSaved" />
    <ConfirmDialog
      v-if="confirmTarget"
      title="删除交易"
      :message="`确认删除「${confirmTarget.itemNameZh ?? confirmTarget.itemName}」这笔交易？删除后不可恢复。`"
      confirm-text="删除"
      danger
      @confirm="onConfirmDelete"
      @cancel="confirmTarget = null"
    />
  </div>
</template>

<style scoped>
.count { font-size: 13px; font-weight: 500; color: var(--text-muted); margin-left: 8px; }
.hint { float: right; font-size: 12px; color: var(--text-muted); font-weight: 400; }
kbd { font-family: var(--font-mono); background: #eef0f3; border: 1px solid var(--border-strong); border-bottom-width: 2px; border-radius: 4px; padding: 0 4px; font-size: 11px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 10px; }
.toolbar .search { width: 200px; }
.toolbar .filter { width: 112px; }
.toolbar .date { width: 140px; }
.spacer { flex: 1; }
.range-group { display: inline-flex; align-items: center; gap: 4px; }
.chip {
  border: 1px solid var(--border); background: var(--surface); color: var(--text-secondary);
  border-radius: 999px; padding: 4px 10px; font-size: 12px; cursor: pointer;
  transition: background var(--motion-fast) ease, color var(--motion-fast) ease, border-color var(--motion-fast) ease;
}
.chip:hover { border-color: var(--border-strong); color: var(--text); }
.chip.active { background: var(--accent-soft); color: var(--accent); border-color: var(--accent); font-weight: 550; }
.date-sep { font-size: 12px; color: var(--text-muted); }
.filter-summary { font-size: 12px; color: var(--text-muted); margin-bottom: 12px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.export-group { display: inline-flex; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); overflow: hidden; }
.export-group .btn { border: none; border-radius: 0; }
.export-group .btn + .btn { border-left: 1px solid var(--border); }
@media (max-width: 640px) {
  .hint { display: none; }
  .toolbar .search { width: 100%; }
  .spacer { display: none; }
  .range-group { width: 100%; }
}
</style>