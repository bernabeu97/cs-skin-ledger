<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import ColumnPicker from '../components/ColumnPicker.vue'
import LotForm from '../components/LotForm.vue'
import LotTable from '../components/LotTable.vue'
import SellForm from '../components/SellForm.vue'
import ItemSelect from '../components/ItemSelect.vue'
import { errorMessage } from '../api/client'
import { useLotsStore } from '../stores/lots'
import { useAlertsStore } from '../stores/alerts'
import { useUiStore } from '../stores/ui'
import { formatDateTime, formatMoney, formatSignedMoney } from '../utils/format'
import { useColumnVisibility } from '../utils/columnVisibility'
import type { AmountMismatch, Item, Lot, LotCreateRequest, LotSellRequest, PriceAlert, ReconcileReport, UuFullJsonImportResult } from '../types'

type SortKey = 'buyTime' | 'buyPrice' | 'sellPrice' | 'profit' | 'quantity'

const LOT_COLUMNS = [
  { key: 'item', label: '饰品' }, { key: 'exterior', label: '磨损' }, { key: 'floatValue', label: '磨损值' },
  { key: 'quantity', label: '数量' }, { key: 'buyPrice', label: '买入价' }, { key: 'buyTime', label: '买入时间' },
  { key: 'buyPlatform', label: '买入平台' }, { key: 'sellPrice', label: '出售价' }, { key: 'actualIncome', label: '实际收入' },
  { key: 'fee', label: '手续费' }, { key: 'sellTime', label: '出售时间' }, { key: 'sellPlatform', label: '出售平台' },
  { key: 'profit', label: '盈亏' }, { key: 'status', label: '状态' }, { key: 'note', label: '备注' }
]
const ALERT_COLUMNS = [
  { key: 'item', label: '饰品' }, { key: 'exterior', label: '磨损' }, { key: 'platform', label: '平台' }, { key: 'condition', label: '条件' },
  { key: 'threshold', label: '阈值' }, { key: 'status', label: '状态' }
]

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
const searchEl = ref<HTMLInputElement | null>(null)
const pendingOnly = ref(false)
const nopriceOnly = ref(false)
const highlightId = ref<number | null>(null)
const route = useRoute()
const alertsStore = useAlertsStore()
const tab = ref<'lots' | 'alerts' | 'trash'>('lots')
const page = ref(1)
const pageSize = ref(50)
const selectedIds = ref<number[]>([])
const crossPageSelected = ref(false)
const showAggregate = ref(false)
const aggregateGroup = ref<'item' | 'category'>('item')
const fillPriceDialog = ref<{ price: string } | null>(null)
const fillPriceBusy = ref(false)
const uuPreview = ref<UuFullJsonImportResult | null>(null)
const pendingUuFile = ref<File | null>(null)
const uuPreviewBusy = ref(false)
const reconcileReport = ref<ReconcileReport | null>(null)
const pendingReconcileFile = ref<File | null>(null)
const reconcileBusy = ref(false)
const reconcileFileEl = ref<HTMLInputElement | null>(null)
const batchBusy = ref(false)
const alertItem = ref<Item | null>(null)
const alertExterior = ref('')
const alertPlatform = ref('uu')
const alertCondition = ref<'gt' | 'lt'>('gt')
const alertThreshold = ref<number | null>(null)
const alertBusy = ref(false)
const deleteAlertTarget = ref<PriceAlert | null>(null)
const uuFileEl = ref<HTMLInputElement | null>(null)
const workbookFileEl = ref<HTMLInputElement | null>(null)
const importingWorkbook = ref(false)
const importingUu = ref(false)
const showUuGuide = ref(false)
const { visibleColumns: lotVisibleColumns } = useColumnVisibility('columns:lots', LOT_COLUMNS)
const { visibleColumns: alertVisibleColumns, isColumnVisible: isAlertColumnVisible } = useColumnVisibility('columns:alerts', ALERT_COLUMNS)

const hasFilters = computed(() => {
  const customActive = range.value === 'custom' && (!!fromDate.value || !!toDate.value)
  return !!q.value || !!status.value || !!platform.value || pendingOnly.value || nopriceOnly.value || range.value === '7' || range.value === '30' || customActive
})

const totalPages = computed(() => store.lotsPage?.totalPages ?? 1)
const totalCount = computed(() => store.lotsPage?.total ?? store.lots.length)
const pageItems = computed(() => store.lotsPage?.items ?? store.lots)
const allPageSelected = computed(() =>
  pageItems.value.length > 0 && pageItems.value.every(l => selectedIds.value.includes(l.id)))
const somePageSelected = computed(() =>
  pageItems.value.some(l => selectedIds.value.includes(l.id)) && !allPageSelected.value)
const selectionCount = computed(() =>
  crossPageSelected.value ? totalCount.value : selectedIds.value.length)

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
  if (nopriceOnly.value) params.noprice = 'true'
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
  await Promise.all([
    store.loadLotsPage({ ...buildQuery(), page: page.value, size: pageSize.value }),
    store.loadSummary(),
    store.loadStats(),
    store.loadAggregate(aggregateGroup.value),
    store.loadTrash()
  ])
}

function refreshWithReset() {
  page.value = 1
  refreshAll()
}

function resetFilters() {
  q.value = ''
  status.value = ''
  platform.value = ''
  pendingOnly.value = false
  nopriceOnly.value = false
  range.value = 'all'
  fromDate.value = ''
  toDate.value = ''
  page.value = 1
  selectedIds.value = []
  crossPageSelected.value = false
  refreshAll()
}

function goPage(next: number) {
  if (next < 1 || next > totalPages.value) return
  page.value = next
  refreshAll()
}

function changePageSize(size: number) {
  pageSize.value = size
  page.value = 1
  refreshAll()
}

function toggleSelect(id: number) {
  if (crossPageSelected.value) {
    crossPageSelected.value = false
    selectedIds.value = pageItems.value.map(l => l.id)
  }
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter(v => v !== id)
    : [...selectedIds.value, id]
}

function toggleSelectAll() {
  if (allPageSelected.value || somePageSelected.value) {
    selectedIds.value = selectedIds.value.filter(id => !pageItems.value.some(l => l.id === id))
    crossPageSelected.value = false
    return
  }
  selectedIds.value = [...new Set([...selectedIds.value, ...pageItems.value.map(l => l.id)])]
}

async function selectAllFiltered() {
  const all = await store.loadLots(buildQuery())
  const ids = all.map(l => l.id)
  selectedIds.value = ids
  crossPageSelected.value = ids.length > 0
  ui.toast('info', crossPageSelected.value ? `已选中全部 ${ids.length} 条筛选结果` : '当前筛选无记录')
}

function clearSelection() {
  selectedIds.value = []
  crossPageSelected.value = false
}

function openFillPrice() {
  fillPriceDialog.value = { price: '' }
}

async function confirmFillPrice() {
  const dialog = fillPriceDialog.value
  if (!dialog || fillPriceBusy.value) return
  const price = Number(dialog.price)
  if (!dialog.price || Number.isNaN(price) || price < 0) {
    ui.toast('error', '请输入有效的买入价（≥ 0）')
    return
  }
  fillPriceBusy.value = true
  try {
    const updated = await store.batchFillPrice(selectedIds.value, price)
    ui.toast('success', `已为 ${updated} 条记录补填买入价 ${formatMoney(price)}`)
    fillPriceDialog.value = null
    clearSelection()
    await refreshAll()
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    fillPriceBusy.value = false
  }
}

async function batchExport(format: 'csv' | 'json' | 'xlsx') {
  if (selectionCount.value === 0) return
  batchBusy.value = true
  try {
    await store.exportLots(format, selectedIds.value)
    ui.toast('info', `已导出选中的 ${selectionCount.value} 条记录（${format.toUpperCase()}）`)
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    batchBusy.value = false
  }
}

async function requestBatchDelete() {
  if (selectionCount.value === 0 || batchBusy.value) return
  batchBusy.value = true
  try {
    const deleted = await store.batchDelete(selectedIds.value)
    const ids = [...selectedIds.value]
    clearSelection()
    await refreshAll()
    ui.toast('success', `已删除 ${deleted} 条记录，可在回收站恢复`, 8000, {
      label: '撤销',
      onClick: async () => {
        try {
          await Promise.all(ids.map(id => store.restoreLot(id)))
          ui.toast('success', `已恢复 ${ids.length} 条记录`)
          await refreshAll()
        } catch (e) {
          ui.toast('error', errorMessage(e))
        }
      }
    })
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    batchBusy.value = false
  }
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

async function requestDelete(lot: Lot) {
  try {
    await store.deleteLot(lot.id)
    await refreshAll()
    ui.toast('success', `「${lot.itemNameZh ?? lot.itemName}」已移入回收站`, 8000, {
      label: '撤销',
      onClick: async () => {
        try {
          await store.restoreLot(lot.id)
          ui.toast('success', '记录已恢复')
          await refreshAll()
        } catch (e) {
          ui.toast('error', errorMessage(e))
        }
      }
    })
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

async function downloadTemplate() {
  try {
    await store.downloadImportTemplate()
  } catch (e) {
    ui.toast('error', errorMessage(e))
  }
}

async function selectTab(next: 'lots' | 'alerts' | 'trash') {
  tab.value = next
  if (next === 'trash') await store.loadTrash()
}

async function onWorkbookSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ui.toast('error', '请选择标准模板的 .xlsx 文件')
    return
  }
  importingWorkbook.value = true
  try {
    const result = await store.importWorkbook(file)
    ui.toast(result.failed ? 'info' : 'success', `Excel 导入完成：新增 ${result.created} 条，重复跳过 ${result.skipped} 条，失败 ${result.failed} 条`, 6500)
    if (result.errors.length) ui.toast('error', result.errors.slice(0, 3).join('；'), 8000)
    await refreshAll()
  } catch (e) {
    ui.toast('error', errorMessage(e), 7000)
  } finally {
    importingWorkbook.value = false
  }
}

async function restore(lot: Lot) {
  try {
    await store.restoreLot(lot.id)
    ui.toast('success', '记录已恢复')
  } catch (e) { ui.toast('error', errorMessage(e)) }
}

async function purge(lot: Lot) {
  if (!window.confirm(`彻底删除“${lot.itemNameZh ?? lot.itemName}”？此操作不可恢复。`)) return
  try {
    await store.purgeLot(lot.id)
    ui.toast('success', '记录已彻底删除')
  } catch (e) { ui.toast('error', errorMessage(e)) }
}

function requestUuImport(forceGuide = false) {
  const importedBefore = window.localStorage.getItem('uu-json-import-completed') === '1'
  if (forceGuide || !importedBefore) {
    showUuGuide.value = true
    return
  }
  uuFileEl.value?.click()
}

async function chooseUuJson() {
  showUuGuide.value = false
  await nextTick()
  uuFileEl.value?.click()
}

async function onUuJsonSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.json')) {
    ui.toast('error', '请选择 JSON 文件')
    return
  }
  uuPreviewBusy.value = true
  try {
    const result = await store.previewUuFullJson(file)
    uuPreview.value = result
    pendingUuFile.value = file
  } catch (e) {
    ui.toast('error', errorMessage(e), 7000)
  } finally {
    uuPreviewBusy.value = false
  }
}

async function confirmImportUu() {
  const file = pendingUuFile.value
  if (!file || importingUu.value) return
  importingUu.value = true
  try {
    const result = await store.importUuFullJson(file)
    window.localStorage.setItem('uu-json-import-completed', '1')
    const imported = result.holdingsImported + result.salesImported
    const skipped = result.holdingsSkippedDuplicates + result.salesSkippedDuplicates
    ui.toast('success', `UU 数据导入完成：新增 ${imported} 条，重复跳过 ${skipped} 条`, 6000)
    if (result.unmatchedSales > 0) {
      ui.toast('info', `${result.unmatchedSales} 条卖出缺少历史买入，买入价暂记为 0`, 7000)
    }
    if (result.errors.length > 0) {
      ui.toast('error', `${result.errors.length} 条记录导入失败，请查看后端日志`, 7000)
    }
    uuPreview.value = null
    pendingUuFile.value = null
    await refreshAll()
  } catch (e) {
    ui.toast('error', errorMessage(e), 7000)
  } finally {
    importingUu.value = false
  }
}

function closeUuPreview() {
  uuPreview.value = null
  pendingUuFile.value = null
}

async function addAlert() {
  if (!alertItem.value || alertThreshold.value == null || !(alertThreshold.value > 0)) return
  alertBusy.value = true
  try {
    await alertsStore.createAlert({
      itemId: alertItem.value.id,
      exterior: alertExterior.value || undefined,
      platform: alertPlatform.value,
      condition: alertCondition.value,
      threshold: alertThreshold.value
    })
    alertItem.value = null
    alertExterior.value = ''
    alertThreshold.value = null
    ui.toast('success', '价格提醒已添加')
  } catch (e) {
    ui.toast('error', String(e))
  } finally {
    alertBusy.value = false
  }
}

async function confirmDeleteAlert() {
  const target = deleteAlertTarget.value
  deleteAlertTarget.value = null
  if (!target) return
  try {
    await alertsStore.deleteAlert(target.id)
    ui.toast('success', '提醒已删除')
  } catch (e) {
    ui.toast('error', String(e))
  }
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
  if (route.query.new === '1') {
    openCreate()
  }
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
  if (route.query.noprice === '1') {
    nopriceOnly.value = true
  }
}

async function onReconcileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.json')) {
    ui.toast('error', '请选择 JSON 文件')
    return
  }
  reconcileBusy.value = true
  try {
    const report = await store.reconcileUuFullJson(file)
    reconcileReport.value = report
    pendingReconcileFile.value = file
  } catch (e) {
    ui.toast('error', errorMessage(e), 7000)
  } finally {
    reconcileBusy.value = false
  }
}

function closeReconcile() {
  reconcileReport.value = null
  pendingReconcileFile.value = null
}

async function importReconciled() {
  const file = pendingReconcileFile.value
  if (!file || importingUu.value) return
  importingUu.value = true
  try {
    const result = await store.importUuFullJson(file)
    ui.toast('success', `已补导平台独有记录：新增 ${result.holdingsImported + result.salesImported} 条，重复跳过 ${result.holdingsSkippedDuplicates + result.salesSkippedDuplicates} 条`, 6000)
    await refreshAll()
    const report = await store.reconcileUuFullJson(file)
    reconcileReport.value = report
  } catch (e) {
    ui.toast('error', errorMessage(e), 7000)
  } finally {
    importingUu.value = false
  }
}

async function confirmFixMismatch(mismatch: AmountMismatch) {
  if (reconcileBusy.value) return
  reconcileBusy.value = true
  try {
    await store.fixPrice(mismatch.id, mismatch.field === 'buyPrice' ? 'buy' : 'sell', mismatch.platformValue)
    ui.toast('success', `已按平台修正「${mismatch.itemName}」${mismatch.field === 'buyPrice' ? '买入价' : '出售价'}为 ${formatMoney(mismatch.platformValue)}`)
    await refreshAll()
    if (pendingReconcileFile.value) {
      reconcileReport.value = await store.reconcileUuFullJson(pendingReconcileFile.value)
    }
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    reconcileBusy.value = false
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKey)
  void (async () => {
    await Promise.all([refreshAll(), alertsStore.loadAlerts()])
    applyRouteTarget()
  })()
})
watch(alertItem, () => { alertExterior.value = '' })
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKey))
</script>

<template>
  <div>
    <div class="page-tabs">
      <button type="button" class="tab-btn" :class="{ active: tab === 'lots' }" @click="selectTab('lots')">饰品账本</button>
      <button type="button" class="tab-btn" :class="{ active: tab === 'alerts' }" @click="selectTab('alerts')">价格提醒</button>
      <button type="button" class="tab-btn" :class="{ active: tab === 'trash' }" @click="selectTab('trash')">回收站</button>
    </div>
    <div v-show="tab === 'lots'">
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
      <input ref="searchEl" v-model="q" class="input search" placeholder="搜索饰品（中/英文）" @keyup.enter="refreshWithReset" />
      <select v-model="status" class="select filter" @change="refreshWithReset">
        <option value="">全部状态</option>
        <option value="HOLDING">持仓中</option>
        <option value="SOLD">已卖出</option>
      </select>
      <button
        type="button"
        class="chip"
        :class="{ active: pendingOnly }"
        @click="pendingOnly = !pendingOnly; refreshWithReset()"
      >待补填买入价</button>
      <button
        type="button"
        class="chip"
        :class="{ active: nopriceOnly }"
        @click="nopriceOnly = !nopriceOnly; refreshWithReset()"
      >无行情</button>
      <select v-model="platform" class="select filter" @change="refreshWithReset">
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
          @click="range = r.v; refreshWithReset()"
        >{{ r.t }}</button>
        <template v-if="range === 'custom'">
          <input v-model="fromDate" class="input date" type="date" @change="refreshWithReset" />
          <span class="date-sep">至</span>
          <input v-model="toDate" class="input date" type="date" @change="refreshWithReset" />
        </template>
      </div>

      <div class="spacer"></div>

      <button type="button" class="btn btn-ghost btn-sm" :class="{ 'chip-active': showAggregate }" @click="showAggregate = !showAggregate">
        {{ showAggregate ? '收起分组' : '分组汇总' }}
      </button>
      <ColumnPicker v-model="lotVisibleColumns" :columns="LOT_COLUMNS" />
      <button type="button" class="btn btn-ghost btn-sm" title="下载标准 Excel 导入模板" @click="downloadTemplate">Excel 模板</button>
      <input ref="workbookFileEl" class="visually-hidden" type="file" accept="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx" @change="onWorkbookSelected" />
      <button type="button" class="btn btn-ghost btn-sm" :disabled="importingWorkbook" @click="workbookFileEl?.click()">{{ importingWorkbook ? '导入中…' : '导入 Excel' }}</button>
      <input ref="uuFileEl" class="visually-hidden" type="file" accept="application/json,.json" @change="onUuJsonSelected" />
      <button
        type="button"
        class="btn btn-ghost btn-sm"
        title="导入悠悠有品全量记录 JSON（重复记录自动跳过）"
        :disabled="importingUu"
        @click="requestUuImport()"
      >{{ importingUu ? '导入中…' : '导入 UU JSON' }}</button>
      <input ref="reconcileFileEl" class="visually-hidden" type="file" accept="application/json,.json" @change="onReconcileSelected" />
      <button type="button" class="btn btn-ghost btn-sm" :disabled="reconcileBusy" @click="reconcileFileEl?.click()">
        {{ reconcileBusy ? '对账中…' : 'UU 对账' }}
      </button>
      <button type="button" class="btn btn-ghost btn-sm" @click="requestUuImport(true)">安装/使用帮助</button>
      <div class="export-group">
        <button type="button" class="btn btn-ghost btn-sm" title="导出 CSV" @click="onExport('csv')">CSV</button>
        <button type="button" class="btn btn-ghost btn-sm" title="导出 JSON" @click="onExport('json')">JSON</button>
        <button type="button" class="btn btn-ghost btn-sm" title="导出 Excel" @click="onExport('xlsx')">Excel</button>
      </div>
      <button type="button" class="btn btn-primary" @click="openCreate">＋ 新增买入</button>
    </div>

    <div v-if="hasFilters" class="filter-summary">
      当前筛选：{{ q ? `关键词“${q}”` : '' }}{{ status === 'HOLDING' ? ' · 持仓中' : status === 'SOLD' ? ' · 已卖出' : '' }}{{ platform ? ` · ${platform}` : '' }}{{ pendingOnly ? ' · 待补填' : '' }}{{ nopriceOnly ? ' · 无行情' : '' }}{{ range === '7' ? ' · 近7天' : range === '30' ? ' · 近30天' : range === 'custom' ? ' · 自定义区间' : '' }}
      <button type="button" class="btn btn-ghost btn-sm" @click="resetFilters">清除</button>
    </div>

    <div v-if="store.error" class="error-banner">
      <span>{{ store.error }}</span>
      <button type="button" class="btn btn-sm" @click="refreshAll">重试</button>
    </div>

    <div v-if="selectionCount > 0" class="selection-bar">
      <span class="selection-count">已选 <b class="num">{{ selectionCount }}</b> 条<template v-if="crossPageSelected">（全部筛选结果）</template></span>
      <button type="button" class="btn btn-sm" :disabled="batchBusy" @click="openFillPrice">批量补填买入价</button>
      <span class="batch-export">
        <button type="button" class="btn btn-sm" :disabled="batchBusy" @click="batchExport('csv')">导出 CSV</button>
        <button type="button" class="btn btn-sm" :disabled="batchBusy" @click="batchExport('xlsx')">导出 Excel</button>
        <button type="button" class="btn btn-sm" :disabled="batchBusy" @click="batchExport('json')">导出 JSON</button>
      </span>
      <button type="button" class="btn btn-danger btn-sm" :disabled="batchBusy" @click="requestBatchDelete">删除所选</button>
      <span class="selection-spacer"></span>
      <button type="button" class="btn btn-ghost btn-sm" :disabled="crossPageSelected" @click="selectAllFiltered">跨页全选</button>
      <button type="button" class="btn btn-ghost btn-sm" @click="clearSelection">清除</button>
    </div>

    <div v-if="showAggregate" class="card aggregate-panel">
      <div class="aggregate-head">
        <h3>分组汇总</h3>
        <div class="aggregate-tabs">
          <button type="button" class="chip" :class="{ active: aggregateGroup === 'item' }" @click="aggregateGroup = 'item'; store.loadAggregate('item')">按单品</button>
          <button type="button" class="chip" :class="{ active: aggregateGroup === 'category' }" @click="aggregateGroup = 'category'; store.loadAggregate('category')">按分类</button>
        </div>
      </div>
      <div class="table-wrap">
        <table class="data">
          <thead><tr><th>名称</th><th class="num-head">已实现盈亏</th><th class="num-head">买入成本</th><th class="num-head">卖出笔数</th><th class="num-head">胜率</th></tr></thead>
          <tbody>
            <tr v-for="row in store.aggregate ?? []" :key="row.key">
              <td class="agg-name" :title="row.key">{{ row.key }}</td>
              <td class="num" :class="row.realizedPnl >= 0 ? 'up' : 'down'">{{ formatSignedMoney(row.realizedPnl) }}</td>
              <td class="num">{{ formatMoney(row.buyCost) }}</td>
              <td class="num">{{ row.soldCount }}</td>
              <td class="num">{{ row.soldCount ? Math.round((row.winningSoldCount / row.soldCount) * 100) + '%' : '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!store.loading && (store.aggregate ?? []).length === 0" class="empty-state compact"><p>暂无已实现盈亏数据。</p></div>
    </div>

    <LotTable
      :lots="sortedLots"
      :loading="store.loadingPage || store.loading"
      :sort-key="sortKey"
      :sort-dir="sortDir"
      :visible-columns="lotVisibleColumns"
      :highlight-id="highlightId"
      :selected-ids="selectedIds"
      @edit="openEdit"
      @delete="requestDelete"
      @sell="sellTarget = $event"
      @sort="toggleSort"
      @toggle-select="toggleSelect"
      @toggle-select-all="toggleSelectAll"
    />

    <div v-if="totalPages > 1 || totalCount > pageSize" class="pagination">
      <span class="pagination-total">共 {{ totalCount }} 条</span>
      <select v-model.number="pageSize" class="select page-size" @change="changePageSize(pageSize)">
        <option :value="20">20 / 页</option>
        <option :value="50">50 / 页</option>
        <option :value="100">100 / 页</option>
      </select>
      <button type="button" class="btn btn-sm" :disabled="page <= 1" @click="goPage(page - 1)">‹ 上一页</button>
      <span class="pagination-index num">{{ page }} / {{ totalPages }}</span>
      <button type="button" class="btn btn-sm" :disabled="page >= totalPages" @click="goPage(page + 1)">下一页 ›</button>
    </div>

    <div v-if="!store.loading && !store.error && store.lots.length === 0" class="empty-state">
      <div class="empty-icon" aria-hidden="true">{{ hasFilters ? '🔍' : '📦' }}</div>
      <p v-if="hasFilters">没有符合当前筛选条件的记录，试试调整或清除筛选。</p>
      <p v-else>还没有买入记录。先录入一笔买入，之后随时可以补填卖出数据计算盈亏。</p>
      <button v-if="hasFilters" type="button" class="btn" @click="resetFilters">清除筛选</button>
      <button v-else type="button" class="btn btn-primary" @click="openCreate">新增第一笔买入</button>
    </div>

    <LotForm v-if="showBuyForm" :editing="editing" :saving="saving" :attention="!!editing && editing.buyPrice === 0" @close="showBuyForm = false" @saved="onSaved" />
    <SellForm v-if="sellTarget" :lot="sellTarget" :saving="saving" @close="sellTarget = null" @saved="onSellSaved" />
    <div v-if="fillPriceDialog" class="dialog-mask" @click.self="fillPriceDialog = null">
      <section class="dialog-panel form-panel fill-price-panel" role="dialog" aria-modal="true" aria-label="批量补填买入价">
        <form novalidate @submit.prevent="confirmFillPrice">
          <div class="form-header"><h2>批量补填买入价</h2><button type="button" class="close-btn" aria-label="关闭" @click="fillPriceDialog = null">×</button></div>
          <div class="fill-price-body">
            <p class="fill-price-desc">将为选中的 <b>{{ selectionCount }}</b> 条记录统一填写买入价（单件单价，元）。已卖出的记录会同时重算盈亏。</p>
            <label class="field"><span>买入价（元）</span><input v-model="fillPriceDialog.price" class="input num" type="number" step="0.01" min="0" placeholder="如 88.5" autofocus /></label>
          </div>
          <div class="form-actions">
            <div class="actions-right">
              <button type="button" class="btn" :disabled="fillPriceBusy" @click="fillPriceDialog = null">取消</button>
              <button type="submit" class="btn btn-primary" :disabled="fillPriceBusy">{{ fillPriceBusy ? '保存中…' : '确认补填' }}</button>
            </div>
          </div>
        </form>
      </section>
    </div>
    <div v-if="uuPreview" class="dialog-mask" role="dialog" aria-modal="true" aria-labelledby="uu-preview-title" @click.self="closeUuPreview">
      <section class="dialog-panel uu-guide">
        <header class="guide-head">
          <div>
            <span class="guide-kicker">UU 增量比对</span>
            <h2 id="uu-preview-title">导入前比对结果</h2>
          </div>
          <button type="button" class="guide-close" aria-label="关闭" @click="closeUuPreview">×</button>
        </header>
        <div class="preview-grid">
          <div class="preview-stat"><span>新增持有</span><b class="num">{{ uuPreview.holdingsImported }}</b></div>
          <div class="preview-stat"><span>重复跳过（持有）</span><b class="num muted">{{ uuPreview.holdingsSkippedDuplicates }}</b></div>
          <div class="preview-stat"><span>新增卖出</span><b class="num">{{ uuPreview.salesImported }}</b></div>
          <div class="preview-stat"><span>重复跳过（卖出）</span><b class="num muted">{{ uuPreview.salesSkippedDuplicates }}</b></div>
          <div class="preview-stat"><span>未匹配卖出（买入价记 0）</span><b class="num warn">{{ uuPreview.unmatchedSales }}</b></div>
          <div class="preview-stat"><span>忽略 / 无效记录</span><b class="num muted">{{ uuPreview.ignoredRecords }}</b></div>
        </div>
        <div v-if="uuPreview.warnings.length" class="preview-warnings">
          <p v-for="(w, i) in uuPreview.warnings" :key="i">{{ w }}</p>
        </div>
        <div v-if="uuPreview.errors.length" class="preview-errors">
          <p v-for="(e, i) in uuPreview.errors" :key="i">{{ e }}</p>
        </div>
        <footer class="guide-actions">
          <span class="guide-spacer"></span>
          <button type="button" class="btn" @click="closeUuPreview">取消</button>
          <button type="button" class="btn btn-primary" :disabled="importingUu || (uuPreview.holdingsImported + uuPreview.salesImported) === 0" @click="confirmImportUu">
            {{ importingUu ? '导入中…' : `确认导入 ${uuPreview.holdingsImported + uuPreview.salesImported} 条` }}
          </button>
        </footer>
      </section>
    </div>
    <div v-if="reconcileReport" class="dialog-mask" role="dialog" aria-modal="true" aria-labelledby="reconcile-title" @click.self="closeReconcile">
      <section class="dialog-panel uu-guide">
        <header class="guide-head">
          <div>
            <span class="guide-kicker">UU 双向对账</span>
            <h2 id="reconcile-title">平台文件 vs 系统账本</h2>
          </div>
          <button type="button" class="guide-close" aria-label="关闭" @click="closeReconcile">×</button>
        </header>
        <div class="preview-grid">
          <div class="preview-stat"><span>平台独有·持有</span><b class="num">{{ reconcileReport.platformOnlyHoldings }}</b></div>
          <div class="preview-stat"><span>平台独有·卖出</span><b class="num">{{ reconcileReport.platformOnlySales }}</b></div>
          <div class="preview-stat"><span>系统独有</span><b class="num warn">{{ reconcileReport.systemOnlyCount }}</b></div>
          <div class="preview-stat"><span>金额不一致</span><b class="num" :class="reconcileReport.amountMismatches.length ? 'warn' : 'up'">{{ reconcileReport.amountMismatches.length }}</b></div>
          <div class="preview-stat"><span>文件记录总数</span><b class="num muted">{{ reconcileReport.totalRecords }}</b></div>
          <div class="preview-stat"><span>待导入</span><b class="num">{{ reconcileReport.platformOnlyHoldings + reconcileReport.platformOnlySales }}</b></div>
        </div>
        <div v-if="reconcileReport.warnings.length" class="preview-warnings">
          <p v-for="(w, i) in reconcileReport.warnings" :key="i">{{ w }}</p>
        </div>
        <div v-if="reconcileReport.amountMismatches.length" class="mismatch-block">
          <h4>金额不一致（需逐条确认）</h4>
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>饰品</th><th>字段</th><th class="num-head">系统值</th><th class="num-head">平台值</th><th></th></tr></thead>
              <tbody>
                <tr v-for="m in reconcileReport.amountMismatches" :key="m.id">
                  <td>{{ m.itemName }}<small v-if="m.exterior"> · {{ m.exterior }}</small></td>
                  <td>{{ m.field === 'buyPrice' ? '买入价' : '出售价' }}</td>
                  <td class="num">{{ m.systemValue == null ? '0.00' : formatMoney(m.systemValue) }}</td>
                  <td class="num" :class="m.platformValue >= (m.systemValue ?? 0) ? 'up' : 'down'">{{ formatMoney(m.platformValue) }}</td>
                  <td class="row-actions">
                    <button type="button" class="btn btn-sm" :disabled="reconcileBusy" @click="confirmFixMismatch(m)">以平台为准修正</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <footer class="guide-actions">
          <span class="guide-spacer"></span>
          <button type="button" class="btn" @click="closeReconcile">关闭</button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="importingUu || (reconcileReport.platformOnlyHoldings + reconcileReport.platformOnlySales) === 0"
            @click="importReconciled"
          >{{ importingUu ? '导入中…' : `导入平台独有 ${reconcileReport.platformOnlyHoldings + reconcileReport.platformOnlySales} 条` }}</button>
        </footer>
      </section>
    </div>
    </div>

    <div v-show="tab === 'trash'">
      <div class="section-title-row"><div><h1>回收站</h1><p class="trash-desc">删除的记录保留 30 天，期间不会计入持仓、盈亏或行情估值。</p></div><span class="badge badge-muted">{{ store.trash.length }} 条</span></div>
      <div v-if="store.trash.length" class="table-wrap"><table class="data"><thead><tr><th>饰品</th><th>买入时间</th><th>买入价</th><th>原状态</th><th>删除时间</th><th>操作</th></tr></thead><tbody>
        <tr v-for="lot in store.trash" :key="lot.id"><td><strong>{{ lot.itemNameZh ?? lot.itemName }}</strong><small v-if="lot.exterior"> · {{ lot.exterior }}</small></td><td>{{ formatDateTime(lot.buyTime) }}</td><td class="num">{{ formatMoney(lot.buyPrice) }}</td><td><span class="badge badge-muted">{{ lot.status === 'SOLD' ? '已卖出' : '持仓中' }}</span></td><td>{{ formatDateTime(lot.deletedAt!) }}</td><td class="row-actions"><button class="btn btn-sm" type="button" @click="restore(lot)">恢复</button><button class="btn btn-ghost btn-sm danger-text" type="button" @click="purge(lot)">彻底删除</button></td></tr>
      </tbody></table></div>
      <div v-else class="empty-state"><p>回收站为空。</p></div>
    </div>

    <div v-show="tab === 'alerts'">
      <div class="section-title-row">
        <h1>价格提醒</h1>
        <ColumnPicker v-model="alertVisibleColumns" :columns="ALERT_COLUMNS" />
      </div>
      <div class="alert-bar">
        <ItemSelect v-model="alertItem" placeholder="搜索要提醒的饰品（支持中文）" />
        <select v-model="alertExterior" class="input" style="width:auto" :disabled="!alertItem">
          <option value="">无磨损 / 不区分</option>
          <option v-for="wear in alertItem?.wears ?? []" :key="wear" :value="wear">{{ wear }}</option>
        </select>
        <span class="badge badge-muted mono">UU</span>
        <select v-model="alertCondition" class="input" style="width:auto">
          <option value="gt">价格高于</option>
          <option value="lt">价格低于</option>
        </select>
        <input
          v-model.number="alertThreshold"
          class="input"
          type="number"
          min="0.01"
          step="0.01"
          placeholder="阈值(元)"
          style="width:120px"
        />
        <button
          type="button"
          class="btn btn-primary"
          :disabled="alertBusy || !alertItem || alertThreshold == null || !(alertThreshold > 0)"
          @click="addAlert"
        >{{ alertBusy ? '添加中…' : '添加提醒' }}</button>
      </div>
      <div v-if="alertsStore.error" class="error-banner"><span>{{ alertsStore.error }}</span></div>
      <div class="table-wrap">
        <table class="data">
          <thead>
            <tr>
              <th v-if="isAlertColumnVisible('item')">饰品</th>
              <th v-if="isAlertColumnVisible('exterior')">磨损</th>
              <th v-if="isAlertColumnVisible('platform')">平台</th>
              <th v-if="isAlertColumnVisible('condition')">条件</th>
              <th v-if="isAlertColumnVisible('threshold')" class="num-head">阈值</th>
              <th v-if="isAlertColumnVisible('status')">状态</th><th></th>
            </tr>
          </thead>
          <tbody v-if="alertsStore.loading">
            <tr><td :colspan="alertVisibleColumns.length + 1"><div class="skeleton" style="height:14px;width:100%"></div></td></tr>
          </tbody>
          <tbody v-else>
            <tr v-for="a in alertsStore.alerts" :key="a.id">
              <td v-if="isAlertColumnVisible('item')">{{ a.itemNameZh ?? a.itemName }}</td>
              <td v-if="isAlertColumnVisible('exterior')">{{ a.exterior ?? '-' }}</td>
              <td v-if="isAlertColumnVisible('platform')"><span class="badge badge-muted mono">{{ a.platform }}</span></td>
              <td v-if="isAlertColumnVisible('condition')">{{ a.condition === 'gt' ? '价格高于' : '价格低于' }}</td>
              <td v-if="isAlertColumnVisible('threshold')" class="num">{{ formatMoney(a.threshold) }}</td>
              <td v-if="isAlertColumnVisible('status')">
                <span v-if="a.triggeredAt" class="badge badge-accent">已触发 {{ formatDateTime(a.triggeredAt) }}</span>
                <span v-else class="badge badge-muted">监控中</span>
              </td>
              <td class="row-actions">
                <button type="button" class="btn btn-ghost btn-sm" @click="alertsStore.resetAlert(a.id)">重置</button>
                <button type="button" class="btn btn-ghost btn-sm danger-text" @click="deleteAlertTarget = a">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!alertsStore.loading && alertsStore.alerts.length === 0" class="empty-state compact">
        <p>还没有价格提醒。添加后，每次“刷新行情”会自动检查是否触发。</p>
      </div>
      <ConfirmDialog
        v-if="deleteAlertTarget"
        title="删除提醒"
        :message="`确认删除「${deleteAlertTarget.itemNameZh ?? deleteAlertTarget.itemName}」的价格提醒？`"
        confirm-text="删除"
        danger
        @confirm="confirmDeleteAlert"
        @cancel="deleteAlertTarget = null"
      />
    </div>

    <div v-if="showUuGuide" class="dialog-mask" role="dialog" aria-modal="true" aria-labelledby="uu-guide-title" @click.self="showUuGuide = false">
      <section class="dialog-panel uu-guide">
        <header class="guide-head">
          <div>
            <span class="guide-kicker">UU JSON 导入</span>
            <h2 id="uu-guide-title">先用 Chrome 扩展导出悠悠有品记录</h2>
          </div>
          <button type="button" class="guide-close" aria-label="关闭" @click="showUuGuide = false">×</button>
        </header>
        <ol class="guide-steps">
          <li><b>下载扩展源码</b><span>仓库暂未提供商店版或安装包，需要下载 ZIP 并解压。</span></li>
          <li><b>在 Chrome 加载</b><span>打开 chrome://extensions，开启“开发者模式”，点击“加载已解压的扩展程序”。</span></li>
          <li><b>导出并选择 JSON</b><span>登录悠悠有品，在页面右下角导出交易记录，再回到这里选择 JSON 文件。</span></li>
        </ol>
        <div class="guide-note">扩展负责导出已完成的买卖记录；撤销订单、赔偿和钱包流水不会自动包含。</div>
        <footer class="guide-actions">
          <a class="btn" href="https://github.com/bernabeu97/youpin898-record-exporter/archive/refs/heads/main.zip" target="_blank" rel="noreferrer">下载 ZIP</a>
          <a class="btn btn-ghost" href="https://github.com/bernabeu97/youpin898-record-exporter" target="_blank" rel="noreferrer">查看 GitHub</a>
          <span class="guide-spacer"></span>
          <button type="button" class="btn btn-primary" @click="chooseUuJson">选择 JSON 文件</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.visually-hidden { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.count { font-size: 13px; font-weight: 500; color: var(--text-muted); margin-left: 8px; }
.hint { float: right; font-size: 12px; color: var(--text-muted); font-weight: 400; }
kbd { font-family: var(--font-mono); background: var(--surface-muted); border: 1px solid var(--border-strong); border-bottom-width: 2px; border-radius: 4px; padding: 0 4px; font-size: 11px; }
.stat-strip { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 16px; }
.stat { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); padding: 10px 14px; display: flex; flex-direction: column; gap: 2px; }
.stat-label { font-size: 11px; color: var(--text-muted); }
.stat-value { font-size: 16px; font-weight: 650; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 10px; }
.section-title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.section-title-row h1 { margin: 0; }
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
.page-tabs { display: flex; gap: 4px; margin-bottom: 16px; border-bottom: 1px solid var(--border); }
.tab-btn { border: none; background: none; padding: 9px 14px; font-size: 13px; font-weight: 550; color: var(--text-secondary); cursor: pointer; border-bottom: 2px solid transparent; margin-bottom: -1px; transition: color var(--motion-fast) ease, border-color var(--motion-fast) ease; }
.tab-btn:hover { color: var(--text); }
.tab-btn.active { color: var(--accent); border-bottom-color: var(--accent); }
.chip-active { border-color: var(--accent) !important; color: var(--accent) !important; }
.selection-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 9px 12px; margin-bottom: 12px; border-radius: var(--radius);
  background: var(--accent-soft); border: 1px solid color-mix(in srgb, var(--accent) 35%, transparent);
}
.selection-count { font-size: 13px; }
.selection-count b { color: var(--accent); }
.batch-export { display: inline-flex; gap: 4px; }
.selection-spacer { flex: 1; }
.aggregate-panel { padding: 14px 16px; margin-bottom: 14px; }
.aggregate-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.aggregate-head h3 { margin: 0; font-size: 15px; }
.aggregate-tabs { display: inline-flex; gap: 4px; }
.aggregate-panel .table-wrap { border: 0; box-shadow: none; border-radius: 0; }
.agg-name { max-width: 360px; overflow: hidden; text-overflow: ellipsis; }
.pagination { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.pagination-total { font-size: 12px; color: var(--text-muted); margin-right: auto; }
.page-size { width: auto; }
.pagination-index { font-size: 13px; color: var(--text-secondary); min-width: 64px; text-align: center; }
.fill-price-panel { max-width: 420px; }
.fill-price-body { padding: 16px 20px 4px; }
.fill-price-desc { margin: 0 0 14px; font-size: 13px; color: var(--text-secondary); line-height: 1.6; }
.fill-price-desc b { color: var(--accent); }
.preview-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; padding: 18px 22px 6px; }
.preview-stat { display: flex; flex-direction: column; gap: 2px; padding: 10px 12px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--surface-muted); }
.preview-stat span { font-size: 11px; color: var(--text-muted); }
.preview-stat b { font-size: 20px; font-weight: 650; }
.preview-stat b.muted { color: var(--text-muted); }
.preview-stat b.warn { color: #b45309; }
.preview-warnings, .preview-errors { margin: 0 22px 6px; }
.preview-warnings p, .preview-errors p { margin: 3px 0; font-size: 12px; color: var(--warn-text); }
.preview-errors p { color: var(--danger); }
.mismatch-block { padding: 2px 22px 14px; }
.mismatch-block h4 { margin: 12px 0 8px; font-size: 13px; }
.mismatch-block small { color: var(--text-muted); }
.mismatch-block .table-wrap { max-height: 260px; }
.alert-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.alert-bar .item-select { flex: 1; min-width: 220px; }
.danger-text { color: var(--danger) !important; }
.empty-state.compact { padding: 12px; }
.trash-desc { margin: 3px 0 0; color: var(--text-secondary); font-size: 12px; }
.uu-guide { max-width: 650px; padding: 0; overflow: hidden; }
.guide-head { display: flex; justify-content: space-between; gap: 16px; padding: 20px 22px 14px; border-bottom: 1px solid var(--border); }
.guide-head h2 { margin: 3px 0 0; font-size: 18px; }
.guide-kicker { font-family: var(--font-mono); color: var(--accent); font-size: 11px; letter-spacing: .08em; }
.guide-close { border: 0; background: transparent; color: var(--text-muted); font-size: 24px; line-height: 1; cursor: pointer; border-radius: var(--radius-sm); }
.guide-close:hover { color: var(--text); background: var(--surface-muted); }
.guide-steps { margin: 0; padding: 18px 22px 8px 58px; counter-reset: guide; }
.guide-steps li { position: relative; list-style: none; display: flex; flex-direction: column; gap: 2px; margin-bottom: 17px; }
.guide-steps li::before { counter-increment: guide; content: counter(guide); position: absolute; left: -36px; top: 0; width: 24px; height: 24px; display: grid; place-items: center; border-radius: 50%; background: var(--accent-soft); color: var(--accent); font-family: var(--font-mono); font-size: 11px; font-weight: 700; }
.guide-steps b { font-size: 13px; }
.guide-steps span { color: var(--text-secondary); font-size: 12px; }
.guide-note { margin: 0 22px 18px; padding: 9px 11px; border: 1px solid var(--warn-border); border-radius: var(--radius-sm); background: var(--warn-bg); color: var(--warn-text); font-size: 11px; }
.guide-actions { display: flex; align-items: center; gap: 7px; padding: 14px 22px; border-top: 1px solid var(--border); background: var(--surface-muted); }
.guide-spacer { flex: 1; }
@media (max-width: 640px) {
  .hint { display: none; }
  .toolbar .search { width: 100%; }
  .spacer { display: none; }
  .range-group { width: 100%; }
  .stat-strip { grid-template-columns: repeat(2, 1fr); }
  .pagination { justify-content: center; }
  .pagination-total { width: 100%; text-align: center; margin-right: 0; }
  .preview-grid { grid-template-columns: repeat(2, 1fr); }
  .guide-actions { align-items: stretch; flex-direction: column; }
  .guide-spacer { display: none; }
}
</style>
