<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import LotForm from '../components/LotForm.vue'
import LotTable from '../components/LotTable.vue'
import SellForm from '../components/SellForm.vue'
import { errorMessage } from '../api/client'
import { useLotsStore } from '../stores/lots'
import { useUiStore } from '../stores/ui'
import { downloadBlob, formatMoney, formatSignedMoney } from '../utils/format'
import type { Lot, LotCreateRequest, LotSellRequest } from '../types'

type SortKey = 'buyTime' | 'buyPrice' | 'sellPrice' | 'profit' | 'quantity'

const store = useLotsStore()
const ui = useUiStore()

const q = ref('')
const status = ref('')
const platform = ref('')
const range = ref<'7' | '30' | 'all' | 'custom'>('all')
const fromDate = ref('')
const toDate = ref('')
const sortKey = ref<SortKey>('buyTime')
const sortDir = ref<'asc' | 'desc'>('desc')
const showBuyForm = ref(false)
const editing = ref<Lot | null>(null)
const sellTarget = ref<Lot | null>(null)
const saving = ref(false)
const confirmTarget = ref<Lot | null>(null)
const searchEl = ref<HTMLInputElement | null>(null)
const pendingOnly = ref(false)
const highlightId = ref<number | null>(null)
const route = useRoute()

const hasFilters = computed(() => {
  const customActive = range.value === 'custom' && (!!fromDate.value || !!toDate.value)
  return !!q.value || !!status.value || !!platform.value || pendingOnly.value || range.value === '7' || range.value === '30' || customActive
})

const sortedLots = computed(() => {
  let arr = [...store.lots]
  if (pendingOnly.value) {
    arr = arr.filter(l => l.status === 'HOLDING' && l.buyPrice === 0)
  }
  const dir = sortDir.value === 'asc' ? 1 : -1
  arr.sort((a, b) => {
    if (sortKey.value === 'buyTime') return a.buyTime.localeCompare(b.buyTime) * dir
    const av = a[sortKey.value]
    const bv = b[sortKey.value]
    return ((av == null ? -1e18 : av) - (bv == null ? -1e18 : bv)) * dir
  })
  return arr
})

function buildQuery(): Record<string, string> {
  const params: Record<string, string> = {}
  if (q.value) params.q = q.value
  if (status.value) params.status = status.value
  if (platform.value) params.platform = platform.value
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

async function refreshAll() {
  await Promise.all([store.loadLots(buildQuery()), store.loadSummary()])
}

function resetFilters() {
  q.value = ''
  status.value = ''
  platform.value = ''
  pendingOnly.value = false
  range.value = 'all'
  fromDate.value = ''
  toDate.value = ''
  refreshAll()
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
  showBuyForm.value = true
}

function openEdit(lot: Lot) {
  editing.value = lot
  showBuyForm.value = true
}

async function onSaved(payload: LotCreateRequest) {
  saving.value = true
  try {
    if (editing.value) {
      await store.updateLot(editing.value.id, payload)
      ui.toast('success', '买入记录已更新')
    } else {
      await store.createLot(payload)
      ui.toast('success', '买入记录已保存')
    }
    showBuyForm.value = false
    await refreshAll()
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    saving.value = false
  }
}

async function onSellSaved(payload: LotSellRequest) {
  const target = sellTarget.value
  if (!target) return
  saving.value = true
  try {
    await store.sellLot(target.id, payload)
    ui.toast('success', '卖出数据已更新，盈亏已计算')
    sellTarget.value = null
    await refreshAll()
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    saving.value = false
  }
}

function requestDelete(lot: Lot) {
  confirmTarget.value = lot
}

async function onConfirmDelete() {
  const lot = confirmTarget.value
  confirmTarget.value = null
  if (!lot) return
  try {
    await store.deleteLot(lot.id)
    ui.toast('success', '记录已删除')
    await refreshAll()
  } catch (e) {
    ui.toast('error', errorMessage(e))
  }
}

async function onExport(format: 'csv' | 'json' | 'xlsx') {
  try {
    await store.exportLots(format)
    ui.toast('info', `已导出 ${format.toUpperCase()} 文件`)
  } catch (e) {
    ui.toast('error', errorMessage(e))
  }
}

function downloadTemplate() {
  const header = '饰品,磨损,磨损值,数量,买入价,买入时间,买入平台,出售价,实际收入,手续费,出售时间,出售平台,盈亏,状态,备注'
  downloadBlob(new Blob(['\uFEFF' + header + '\n'], { type: 'text/csv' }), 'lots_template.csv')
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

function applyRouteTarget() {
  const id = Number(route.query.lotId)
  if (id) {
    highlightId.value = id
    const lot = store.lots.find(l => l.id === id)
    if (lot && lot.status === 'HOLDING') {
      sellTarget.value = lot
    }
    nextTick(() => {
      document.querySelector(`tr[data-lot="${id}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    })
  }
  if (route.query.pending === '1') {
    pendingOnly.value = true
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKey)
  void (async () => {
    await refreshAll()
    applyRouteTarget()
  })()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKey))
</script>

<template>
  <div>
    <h1>
      饰品账本
      <span v-if="!store.loading" class="count">{{ store.lots.length }} 条</span>
      <span class="hint">按 <kbd>N</kbd> 新增买入 · 按 <kbd>/</kbd> 搜索</span>
    </h1>

    <div class="stat-strip" v-if="store.summary">
      <div class="stat">
        <span class="stat-label">总买入成本</span>
        <span class="stat-value num">{{ formatMoney(store.summary.totalBuyCost) }}</span>
      </div>
      <div class="stat">
        <span class="stat-label">持仓成本</span>
        <span class="stat-value num">{{ formatMoney(store.summary.holdingCost) }}</span>
      </div>
      <div class="stat">
        <span class="stat-label">已实现盈亏</span>
        <span class="stat-value num" :class="store.summary.realizedProfit >= 0 ? 'up' : 'down'">
          {{ formatSignedMoney(store.summary.realizedProfit) }}
        </span>
      </div>
      <div class="stat">
        <span class="stat-label">待卖批次</span>
        <span class="stat-value num">{{ store.summary.holdingCount }}</span>
      </div>
    </div>

    <div class="toolbar">
      <input ref="searchEl" v-model="q" class="input search" placeholder="搜索饰品（中/英文）" @keyup.enter="refreshAll" />
      <select v-model="status" class="select filter" @change="refreshAll">
        <option value="">全部状态</option>
        <option value="HOLDING">持仓中</option>
        <option value="SOLD">已卖出</option>
      </select>
      <button
        type="button"
        class="chip"
        :class="{ active: pendingOnly }"
        @click="pendingOnly = !pendingOnly; refreshAll()"
      >待补填买入价</button>
      <select v-model="platform" class="select filter" @change="refreshAll">
        <option value="">全部平台</option>
        <option value="steam">Steam</option>
        <option value="uu">UU</option>
        <option value="buff">BUFF</option>
      </select>

      <div class="range-group">
        <button
          v-for="r in [{v:'all',t:'全部'},{v:'7',t:'近7天'},{v:'30',t:'近30天'},{v:'custom',t:'自定义'}] as const"
          :key="r.v"
          type="button"
          class="chip"
          :class="{ active: range === r.v }"
          @click="range = r.v; refreshAll()"
        >{{ r.t }}</button>
        <template v-if="range === 'custom'">
          <input v-model="fromDate" class="input date" type="date" @change="refreshAll" />
          <span class="date-sep">至</span>
          <input v-model="toDate" class="input date" type="date" @change="refreshAll" />
        </template>
      </div>

      <div class="spacer"></div>

      <button type="button" class="btn btn-ghost btn-sm" title="下载导入模板" @click="downloadTemplate">模板</button>
      <div class="export-group">
        <button type="button" class="btn btn-ghost btn-sm" title="导出 CSV" @click="onExport('csv')">CSV</button>
        <button type="button" class="btn btn-ghost btn-sm" title="导出 JSON" @click="onExport('json')">JSON</button>
        <button type="button" class="btn btn-ghost btn-sm" title="导出 Excel" @click="onExport('xlsx')">Excel</button>
      </div>
      <button type="button" class="btn btn-primary" @click="openCreate">＋ 新增买入</button>
    </div>

    <div v-if="hasFilters" class="filter-summary">
      当前筛选：{{ q ? `关键词“${q}”` : '' }}{{ status === 'HOLDING' ? ' · 持仓中' : status === 'SOLD' ? ' · 已卖出' : '' }}{{ platform ? ` · ${platform}` : '' }}{{ range === '7' ? ' · 近7天' : range === '30' ? ' · 近30天' : range === 'custom' ? ' · 自定义区间' : '' }}
      <button type="button" class="btn btn-ghost btn-sm" @click="resetFilters">清除</button>
    </div>

    <div v-if="store.error" class="error-banner">
      <span>{{ store.error }}</span>
      <button type="button" class="btn btn-sm" @click="refreshAll">重试</button>
    </div>

    <LotTable
      :lots="sortedLots"
      :loading="store.loading"
      :sort-key="sortKey"
      :sort-dir="sortDir"
      :highlight-id="highlightId"
      @edit="openEdit"
      @delete="requestDelete"
      @sell="sellTarget = $event"
      @sort="toggleSort"
    />

    <div v-if="!store.loading && !store.error && store.lots.length === 0" class="empty-state">
      <div class="empty-icon" aria-hidden="true">{{ hasFilters ? '🔍' : '📦' }}</div>
      <p v-if="hasFilters">没有符合当前筛选条件的记录，试试调整或清除筛选。</p>
      <p v-else>还没有买入记录。先录入一笔买入，之后随时可以补填卖出数据计算盈亏。</p>
      <button v-if="hasFilters" type="button" class="btn" @click="resetFilters">清除筛选</button>
      <button v-else type="button" class="btn btn-primary" @click="openCreate">新增第一笔买入</button>
    </div>

    <LotForm v-if="showBuyForm" :editing="editing" :saving="saving" :attention="!!editing && editing.buyPrice === 0" @close="showBuyForm = false" @saved="onSaved" />
    <SellForm v-if="sellTarget" :lot="sellTarget" :saving="saving" @close="sellTarget = null" @saved="onSellSaved" />
    <ConfirmDialog
      v-if="confirmTarget"
      title="删除记录"
      :message="`确认删除「${confirmTarget.itemNameZh ?? confirmTarget.itemName}」这条买入记录？删除后不可恢复。`"
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
.stat-strip { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 16px; }
.stat { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 10px 14px; display: flex; flex-direction: column; gap: 2px; }
.stat-label { font-size: 11px; color: var(--text-muted); }
.stat-value { font-size: 16px; font-weight: 650; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 10px; }
.toolbar .search { width: 200px; }
.toolbar .filter { width: 112px; }
.toolbar .date { width: 140px; }
.spacer { flex: 1; }
.range-group { display: inline-flex; align-items: center; gap: 4px; }
.chip { border: 1px solid var(--border); background: var(--surface); color: var(--text-secondary); border-radius: 999px; padding: 4px 10px; font-size: 12px; cursor: pointer; transition: background var(--motion-fast) ease, color var(--motion-fast) ease, border-color var(--motion-fast) ease; }
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
  .stat-strip { grid-template-columns: repeat(2, 1fr); }
}
</style>