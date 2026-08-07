<script setup lang="ts">
import { onMounted, ref } from 'vue'
import TradeForm from '../components/TradeForm.vue'
import TradeTable from '../components/TradeTable.vue'
import { useTradesStore } from '../stores/trades'
import type { Trade, TradeCreateRequest } from '../types'

const store = useTradesStore()
const q = ref('')
const platform = ref('')
const direction = ref('')
const showForm = ref(false)
const editing = ref<Trade | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

async function applyFilters() {
  await store.loadTrades({
    q: q.value || undefined,
    platform: platform.value || undefined,
    direction: direction.value || undefined
  })
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
  if (editing.value) {
    await store.updateTrade(editing.value.id, payload)
  } else {
    await store.createTrade(payload)
  }
  showForm.value = false
  await applyFilters()
}

async function onDelete(t: Trade) {
  if (confirm(`确认删除「${t.itemName}」这笔交易？`)) {
    await store.deleteTrade(t.id)
    await applyFilters()
  }
}

async function onImport() {
  const file = fileInput.value?.files?.[0]
  if (!file) return
  const result = await store.importCsv(file)
  const msg = `导入完成：成功 ${result.created} 条，失败 ${result.failed} 条`
    + (result.errors.length ? '\n' + result.errors.join('\n') : '')
  alert(msg)
  if (fileInput.value) fileInput.value.value = ''
  await applyFilters()
}

onMounted(applyFilters)
</script>

<template>
  <div>
    <h1>交易记录</h1>
    <div class="toolbar">
      <input v-model="q" placeholder="搜索饰品名称" @keyup.enter="applyFilters" />
      <select v-model="platform" @change="applyFilters">
        <option value="">全部平台</option>
        <option value="steam">Steam</option>
        <option value="uu">UU</option>
        <option value="buff">BUFF</option>
      </select>
      <select v-model="direction" @change="applyFilters">
        <option value="">全部方向</option>
        <option value="BUY">买入</option>
        <option value="SELL">卖出</option>
      </select>
      <button @click="openCreate">新增交易</button>
      <button @click="store.exportTrades('csv')">导出 CSV</button>
      <button @click="store.exportTrades('json')">导出 JSON</button>
      <button @click="store.exportTrades('xlsx')">导出 Excel</button>
      <label class="btn">
        导入 CSV
        <input ref="fileInput" type="file" accept=".csv" style="display:none" @change="onImport" />
      </label>
    </div>
    <p v-if="store.error" class="error">{{ store.error }}</p>
    <TradeTable :trades="store.trades" @edit="openEdit" @delete="onDelete" />

    <TradeForm v-if="showForm" :editing="editing" @close="showForm = false" @saved="onSaved" />
  </div>
</template>

<style scoped>
.toolbar { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.toolbar input, .toolbar select { padding: 6px 8px; }
.toolbar button, .btn { padding: 6px 12px; cursor: pointer; border: 1px solid #bbb; background: #fff; border-radius: 4px; }
.error { color: #c00; }
</style>