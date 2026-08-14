<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useLotsStore } from '../stores/lots'
import { useCostsStore } from '../stores/costs'
import { formatMoney, formatSignedMoney } from '../utils/format'

const lots = useLotsStore()
const costs = useCostsStore()
const month = ref(new Date().toISOString().slice(0, 7))
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    await Promise.all([lots.loadLots(), costs.loadCosts(), lots.loadValuation()])
  } finally {
    loading.value = false
  }
}

const monthLots = computed(() => {
  const prefix = month.value
  return lots.lots.filter(l => l.status === 'SOLD' && l.sellTime && l.sellTime.startsWith(prefix))
})
const monthCosts = computed(() => costs.costs.filter(c => c.occurredAt.startsWith(month.value)))

const realized = computed(() => monthLots.value.reduce((sum, l) => sum + (l.profit ?? 0), 0))
const soldCount = computed(() => monthLots.value.length)
const winningCount = computed(() => monthLots.value.filter(l => (l.profit ?? 0) > 0).length)
const winRate = computed(() => (soldCount.value ? (winningCount.value / soldCount.value) : null))
const costIncome = computed(() => monthCosts.value.filter(c => c.direction === 'income').reduce((s, c) => s + c.amount, 0))
const costExpense = computed(() => monthCosts.value.filter(c => c.direction === 'expense').reduce((s, c) => s + c.amount, 0))
const costNet = computed(() => costIncome.value - costExpense.value)
const totalWithCosts = computed(() => realized.value + costNet.value)

function topRows(n: number, loss: boolean) {
  const grouped = new Map<string, number>()
  for (const l of monthLots.value) {
    const name = l.itemNameZh ?? l.itemName
    grouped.set(name, (grouped.get(name) ?? 0) + (l.profit ?? 0))
  }
  return [...grouped.entries()]
    .map(([name, profit]) => ({ name, profit }))
    .sort((a, b) => (loss ? a.profit - b.profit : b.profit - a.profit))
    .slice(0, n)
}
const topProfit = computed(() => topRows(5, false))
const topLoss = computed(() => topRows(5, true))
const platformRows = computed(() => {
  const map = new Map<string, number>()
  for (const l of monthLots.value) {
    const platform = l.sellPlatform ?? '未知'
    map.set(platform, (map.get(platform) ?? 0) + (l.profit ?? 0))
  }
  return [...map.entries()].map(([platform, profit]) => ({ platform, profit }))
    .sort((a, b) => b.profit - a.profit)
})

function printReport() {
  window.print()
}

onMounted(load)
</script>

<template>
  <div class="report-page">
    <div class="page-head no-print">
      <div>
        <h1>月度报告</h1>
        <p>单月盈亏、胜率、TOP 与平台分布,可打印为 PDF。</p>
      </div>
      <div class="head-actions">
        <input v-model="month" class="input month-input" type="month" @change="load" />
        <button type="button" class="btn" :disabled="loading" @click="load">{{ loading ? '加载中…' : '刷新' }}</button>
        <button type="button" class="btn btn-primary" @click="printReport">打印 / 导出 PDF</button>
      </div>
    </div>

    <div v-if="loading" class="report-body">
      <div class="skeleton" style="height:120px;width:100%"></div>
    </div>
    <div v-else class="report-body">
      <div class="report-title">
        <h2>SkinLedger 月度报告 · {{ month }}</h2>
        <span>生成时间 {{ new Date().toLocaleString('zh-CN') }}</span>
      </div>

      <div class="report-cards">
        <div class="card metric">
          <span class="metric-label">饰品已实现盈亏</span>
          <div class="metric-value num" :class="realized >= 0 ? 'up' : 'down'">{{ formatSignedMoney(realized) }}</div>
          <span class="metric-sub">{{ soldCount }} 笔卖出</span>
        </div>
        <div class="card metric">
          <span class="metric-label">含其他收支合计</span>
          <div class="metric-value num" :class="totalWithCosts >= 0 ? 'up' : 'down'">{{ formatSignedMoney(totalWithCosts) }}</div>
          <span class="metric-sub">其他收支净额 {{ formatSignedMoney(costNet) }}</span>
        </div>
        <div class="card metric">
          <span class="metric-label">胜率</span>
          <div class="metric-value num">{{ winRate == null ? '-' : Math.round(winRate * 100) + '%' }}</div>
          <span class="metric-sub">{{ winningCount }} / {{ soldCount }} 笔盈利</span>
        </div>
        <div class="card metric">
          <span class="metric-label">当前市值</span>
          <div class="metric-value num">{{ formatMoney(lots.valuation?.marketValue ?? 0) }}</div>
          <span class="metric-sub">{{ lots.valuation?.priceAsOf ? '截至 ' + lots.valuation.priceAsOf.slice(0, 16).replace('T', ' ') : '暂无行情' }}</span>
        </div>
      </div>

      <div class="report-grid">
        <section class="card report-section">
          <h3>盈利 TOP5</h3>
          <div v-if="topProfit.length" class="rank-list">
            <div v-for="(row, i) in topProfit" :key="row.name" class="rank-row">
              <span class="rank-index">{{ i + 1 }}</span>
              <span class="rank-name" :title="row.name">{{ row.name }}</span>
              <span class="num up">{{ formatSignedMoney(row.profit) }}</span>
            </div>
          </div>
          <p v-else class="empty-hint">本月暂无盈利卖出</p>
        </section>
        <section class="card report-section">
          <h3>亏损 TOP5</h3>
          <div v-if="topLoss.length" class="rank-list">
            <div v-for="(row, i) in topLoss" :key="row.name" class="rank-row">
              <span class="rank-index">{{ i + 1 }}</span>
              <span class="rank-name" :title="row.name">{{ row.name }}</span>
              <span class="num down">{{ formatSignedMoney(row.profit) }}</span>
            </div>
          </div>
          <p v-else class="empty-hint">本月没有亏损卖出</p>
        </section>
        <section class="card report-section">
          <h3>平台分布</h3>
          <div v-if="platformRows.length" class="rank-list">
            <div v-for="row in platformRows" :key="row.platform" class="rank-row">
              <span class="rank-name">{{ row.platform }}</span>
              <span class="num" :class="row.profit >= 0 ? 'up' : 'down'">{{ formatSignedMoney(row.profit) }}</span>
            </div>
          </div>
          <p v-else class="empty-hint">本月无卖出</p>
        </section>
        <section class="card report-section">
          <h3>其他收支</h3>
          <div class="rank-list">
            <div class="rank-row"><span class="rank-name">收入</span><span class="num up">{{ formatMoney(costIncome) }}</span></div>
            <div class="rank-row"><span class="rank-name">支出</span><span class="num down">{{ formatMoney(costExpense) }}</span></div>
            <div class="rank-row"><span class="rank-name">净额</span><span class="num" :class="costNet >= 0 ? 'up' : 'down'">{{ formatSignedMoney(costNet) }}</span></div>
            <p v-if="monthCosts.length === 0" class="empty-hint">本月无其他收支记录</p>
          </div>
        </section>
      </div>

      <section class="card report-section wide">
        <h3>本月卖出明细（{{ monthLots.length }} 条）</h3>
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>饰品</th><th>数量</th><th class="num-head">买入价</th><th class="num-head">出售价</th><th class="num-head">手续费</th><th class="num-head">盈亏</th><th>出售时间</th><th>平台</th></tr></thead>
            <tbody>
              <tr v-for="lot in monthLots.slice(0, 100)" :key="lot.id">
                <td>{{ lot.itemNameZh ?? lot.itemName }}<small v-if="lot.exterior"> · {{ lot.exterior }}</small></td>
                <td class="num">{{ lot.quantity }}</td>
                <td class="num">{{ formatMoney(lot.buyPrice) }}</td>
                <td class="num">{{ lot.sellPrice == null ? '-' : formatMoney(lot.sellPrice) }}</td>
                <td class="num">{{ formatMoney(lot.fee) }}</td>
                <td class="num" :class="(lot.profit ?? 0) >= 0 ? 'up' : 'down'">{{ formatSignedMoney(lot.profit) }}</td>
                <td class="mono">{{ lot.sellTime?.slice(0, 16).replace('T', ' ') }}</td>
                <td>{{ lot.sellPlatform ?? '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 18px; }
.page-head h1 { margin-bottom: 3px; }
.page-head p { margin: 0; color: var(--text-muted); font-size: 12px; }
.head-actions { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.month-input { width: 150px; }
.report-title { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.report-title h2 { margin: 0; }
.report-title span { color: var(--text-muted); font-size: 11px; }
.report-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.metric { padding: 14px 16px; display: flex; flex-direction: column; gap: 3px; }
.metric-label { font-size: 12px; font-weight: 550; color: var(--text-secondary); }
.metric-value { font-size: 22px; font-weight: 650; letter-spacing: -.01em; }
.metric-sub { font-size: 11px; color: var(--text-muted); }
.report-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 16px; }
.report-section { padding: 14px 16px; }
.report-section.wide { grid-column: 1 / -1; }
.report-section h3 { margin: 0 0 10px; font-size: 14px; }
.rank-list { display: flex; flex-direction: column; gap: 6px; }
.rank-row { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.rank-index { width: 20px; height: 20px; flex: none; display: grid; place-items: center; border-radius: 6px; background: var(--accent-soft); color: var(--accent); font: 700 11px/1 var(--font-mono); }
.rank-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-row small { color: var(--text-muted); }
.report-section .data small { color: var(--text-muted); }
.empty-hint { margin: 0; color: var(--text-muted); font-size: 12px; }
.up { color: var(--success); }
.down { color: var(--danger); }
@media (max-width: 900px) {
  .report-cards { grid-template-columns: repeat(2, 1fr); }
  .report-grid { grid-template-columns: 1fr; }
}
@media print {
  .no-print { display: none !important; }
  body { background: #fff; }
  .card, .table-wrap { box-shadow: none; backdrop-filter: none; }
  .report-page { max-width: 100%; }
}
</style>
