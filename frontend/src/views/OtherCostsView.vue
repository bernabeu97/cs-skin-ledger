<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useCostsStore } from '../stores/costs'
import ItemSelect from '../components/ItemSelect.vue'
import { formatDateTime, formatMoney, formatSignedMoney } from '../utils/format'
import { COST_CATEGORY_LABELS } from '../types'
import type { CostCategory, CostDirection, CostRequest, Item } from '../types'

const store = useCostsStore()

const filterCategory = ref('')
const filterDirection = ref('')
const showForm = ref(false)
const editingId = ref<number | null>(null)
const busy = ref(false)
const formError = ref('')

const form = ref<{
  category: CostCategory
  direction: CostDirection
  amount: number | null
  occurredAt: string
  platform: string
  item: Item | null
  note: string
}>({
  category: 'membership',
  direction: 'expense',
  amount: null,
  occurredAt: '',
  platform: 'uu',
  item: null,
  note: ''
})

const CATEGORIES = Object.entries(COST_CATEGORY_LABELS) as [CostCategory, string][]
const summary = computed(() => store.summary)

async function loadAll() {
  await Promise.all([store.loadCosts(), store.loadSummary()])
}

function applyFilters() {
  store.loadCosts({
    category: filterCategory.value || undefined,
    direction: filterDirection.value || undefined
  })
}

function openCreate() {
  editingId.value = null
  form.value = { category: 'membership', direction: 'expense', amount: null, occurredAt: '', platform: 'uu', item: null, note: '' }
  formError.value = ''
  showForm.value = true
}

function openEdit(c: NonNullable<typeof store.costs>[number]) {
  editingId.value = c.id
  form.value = {
    category: c.category,
    direction: c.direction,
    amount: c.amount,
    occurredAt: c.occurredAt,
    platform: c.platform ?? 'uu',
    item: c.itemId ? { id: c.itemId, marketHashName: c.itemName ?? '', nameZh: c.itemNameZh ?? null, weapon: null, category: null, minFloat: null, maxFloat: null, wears: null } as Item : null,
    note: c.note ?? ''
  }
  formError.value = ''
  showForm.value = true
}

async function save() {
  if (!form.value.amount || !(form.value.amount > 0)) {
    formError.value = '金额必须大于 0'
    return
  }
  if (!form.value.occurredAt) {
    formError.value = '请选择时间'
    return
  }
  let time = form.value.occurredAt
  if (!/:\d{2}$/.test(time)) time += ':00'
  const payload: CostRequest = {
    category: form.value.category,
    direction: form.value.direction,
    amount: form.value.amount,
    occurredAt: time,
    platform: form.value.platform || undefined,
    itemId: form.value.item?.id,
    note: form.value.note || undefined
  }
  busy.value = true
  formError.value = ''
  try {
    if (editingId.value != null) {
      await store.updateCost(editingId.value, payload)
    } else {
      await store.createCost(payload)
    }
    showForm.value = false
  } catch (e) {
    formError.value = String(e)
  } finally {
    busy.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>其他收支</h1>
      <div class="head-actions">
        <button type="button" class="btn" @click="store.exportCosts('csv')">导出 CSV</button>
        <button type="button" class="btn" @click="store.exportCosts('xlsx')">导出 Excel</button>
        <button type="button" class="btn btn-primary" @click="openCreate">新增记录</button>
      </div>
    </div>

    <div class="cards">
      <div class="card metric">
        <span class="metric-label">其他收入</span>
        <div class="metric-value num up">{{ formatMoney(summary?.totalIncome ?? 0) }}</div>
      </div>
      <div class="card metric">
        <span class="metric-label">其他支出</span>
        <div class="metric-value num down">{{ formatMoney(summary?.totalExpense ?? 0) }}</div>
      </div>
      <div class="card metric">
        <span class="metric-label">其他收支净额</span>
        <div class="metric-value num" :class="(summary?.net ?? 0) >= 0 ? 'up' : 'down'">
          {{ formatSignedMoney(summary?.net ?? 0) }}
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <select v-model="filterCategory" class="input" @change="applyFilters">
        <option value="">全部分类</option>
        <option v-for="[k, label] in CATEGORIES" :key="k" :value="k">{{ label }}</option>
      </select>
      <select v-model="filterDirection" class="input" @change="applyFilters">
        <option value="">全部方向</option>
        <option value="expense">支出</option>
        <option value="income">收入</option>
      </select>
      <button type="button" class="btn" @click="filterCategory=''; filterDirection=''; applyFilters()">重置</button>
    </div>

    <form v-if="showForm" class="card form-panel" @submit.prevent="save">
      <h3>{{ editingId != null ? '编辑记录' : '新增记录' }}</h3>
      <div class="form-grid">
        <label class="field">
          <span>分类</span>
          <select v-model="form.category" class="input">
            <option v-for="[k, label] in CATEGORIES" :key="k" :value="k">{{ label }}</option>
          </select>
        </label>
        <label class="field">
          <span>方向</span>
          <select v-model="form.direction" class="input">
            <option value="expense">支出</option>
            <option value="income">收入</option>
          </select>
        </label>
        <label class="field">
          <span>金额（元）</span>
          <input v-model.number="form.amount" class="input" type="number" min="0.01" step="0.01" placeholder="如 999" />
        </label>
        <label class="field">
          <span>时间</span>
          <input v-model="form.occurredAt" class="input" type="datetime-local" />
        </label>
        <label class="field">
          <span>平台</span>
          <select v-model="form.platform" class="input">
            <option value="uu">UU</option>
            <option value="buff">BUFF</option>
            <option value="steam">Steam</option>
            <option value="">未知</option>
          </select>
        </label>
        <label class="field">
          <span>关联饰品（可选）</span>
          <ItemSelect v-model="form.item" placeholder="搜索关联的饰品" />
        </label>
        <label class="field field-wide">
          <span>备注</span>
          <input v-model="form.note" class="input" placeholder="如：预售未发货赔偿 / 会员费" />
        </label>
      </div>
      <p v-if="formError" class="field-error">{{ formError }}</p>
      <div class="form-actions">
        <button type="button" class="btn" @click="showForm = false">取消</button>
        <button type="submit" class="btn btn-primary" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button>
      </div>
    </form>

    <div v-if="store.error" class="error-banner"><span>{{ store.error }}</span></div>

    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th>分类</th><th>方向</th><th class="num-head">金额</th><th class="num-head">时间</th>
            <th>平台</th><th>关联饰品</th><th>备注</th><th></th>
          </tr>
        </thead>
        <tbody v-if="store.loading">
          <tr><td colspan="8"><div class="skeleton" style="height:14px;width:100%"></div></td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="c in store.costs" :key="c.id">
            <td><span class="badge" :class="c.direction === 'income' ? 'badge-success' : 'badge-danger'">{{ COST_CATEGORY_LABELS[c.category] }}</span></td>
            <td>{{ c.direction === 'income' ? '收入' : '支出' }}</td>
            <td class="num" :class="c.direction === 'income' ? 'up' : 'down'">{{ formatSignedMoney(c.amount) }}</td>
            <td class="num mono">{{ formatDateTime(c.occurredAt) }}</td>
            <td><span class="badge badge-muted mono">{{ c.platform ?? '-' }}</span></td>
            <td>{{ c.itemNameZh ?? c.itemName ?? '-' }}</td>
            <td class="note-cell">{{ c.note ?? '-' }}</td>
            <td class="row-actions">
              <button type="button" class="btn btn-ghost btn-sm" @click="openEdit(c)">编辑</button>
              <button type="button" class="btn btn-ghost btn-sm danger-text" @click="store.deleteCost(c.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!store.loading && store.costs.length === 0" class="empty-state compact">
      <p>还没有其他收支记录，点击“新增记录”添加会员费、赔偿等。</p>
    </div>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.page-head h1 { margin-bottom: 16px; }
.head-actions { display: inline-flex; gap: 8px; margin-bottom: 16px; }
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 20px; }
.metric { padding: 14px 18px; }
.metric-label { font-size: 12px; font-weight: 550; color: var(--text-secondary); }
.metric-value { font-size: 22px; font-weight: 650; letter-spacing: -.01em; margin-top: 4px; }
.up { color: var(--success); }
.down { color: var(--danger); }
.filter-bar { display: flex; gap: 8px; margin-bottom: 14px; flex-wrap: wrap; }
.filter-bar .input { width: auto; }
.form-panel { padding: 16px 18px; margin-bottom: 16px; }
.form-panel h3 { margin-bottom: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.field-wide { grid-column: span 3; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.note-cell { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
table.data th.num-head, table.data td.num { text-align: right; font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
.danger-text { color: var(--danger) !important; }
.empty-state.compact { padding: 12px; }
@media (max-width: 760px) {
  .cards { grid-template-columns: 1fr; }
  .form-grid { grid-template-columns: 1fr; }
  .field-wide { grid-column: span 1; }
}
</style>