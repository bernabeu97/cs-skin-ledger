<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import TradeForm from '../components/TradeForm.vue'
import TradeTable from '../components/TradeTable.vue'
import { errorMessage } from '../api/client'
import { useTradesStore } from '../stores/trades'
import { useUiStore } from '../stores/ui'
import type { Trade, TradeCreateRequest } from '../types'

const store = useTradesStore()
const ui = useUiStore()

const q = ref('')
const platform = ref('')
const direction = ref('')
const showForm = ref(false)
const editing = ref<Trade | null>(null)
const saving = ref(false)
const confirmTarget = ref<Trade | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const hasFilters = computed(() => !!q.value || !!platform.value || !!direction.value)

async function applyFilters() {
  await store.loadTrades({
    q: q.value || undefined,
    platform: platform.value || undefined,
    direction: direction.value || undefined
  })
}

function resetFilters() {
  q.value = ''
  platform.value = ''
  direction.value = ''
  applyFilters()
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

onMounted(applyFilters)
</script>

<template>
  <div>
    <h1>交易记录 <span v-if="!store.loading" class="count">{{ store.trades.length }} 笔</span></h1>

    <div class="toolbar">
      <input v-model="q" class="input search" placeholder="搜索饰品名称" @keyup.enter="applyFilters" />
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
      <button v-if="hasFilters" type="button" class="btn btn-ghost" @click="resetFilters">重置筛选</button>

      <div class="spacer"></div>

      <label class="btn">
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

    <div v-if="store.error" class="error-banner">
      <span>{{ store.error }}</span>
      <button type="button" class="btn btn-sm" @click="applyFilters">重试</button>
    </div>

    <TradeTable :trades="store.trades" :loading="store.loading" @edit="openEdit" @delete="requestDelete" />

    <div v-if="!store.loading && !store.error && store.trades.length === 0" class="empty-state">
      <div class="empty-icon" aria-hidden="true">📦</div>
      <p>还没有交易记录，先录入一笔买入或卖出，盈亏统计会自动生成。</p>
      <button type="button" class="btn btn-primary" @click="openCreate">新增第一笔交易</button>
    </div>

    <TradeForm v-if="showForm" :editing="editing" :saving="saving" @close="showForm = false" @saved="onSaved" />
    <ConfirmDialog
      v-if="confirmTarget"
      title="删除交易"
      :message="`确认删除「${confirmTarget.itemName}」这笔交易？删除后不可恢复。`"
      confirm-text="删除"
      danger
      @confirm="onConfirmDelete"
      @cancel="confirmTarget = null"
    />
  </div>
</template>

<style scoped>
.count { font-size: 13px; font-weight: 500; color: var(--text-muted); margin-left: 8px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 16px; }
.toolbar .search { width: 220px; }
.toolbar .filter { width: 120px; }
.spacer { flex: 1; }
.export-group { display: inline-flex; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); overflow: hidden; }
.export-group .btn { border: none; border-radius: 0; }
.export-group .btn + .btn { border-left: 1px solid var(--border); }
@media (max-width: 640px) {
  .toolbar .search { width: 100%; }
  .spacer { display: none; }
}
</style>