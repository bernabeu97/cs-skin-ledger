<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PnlChart from '../components/PnlChart.vue'
import { useLotsStore } from '../stores/lots'
import { formatDateTime, formatMoney, formatQty, formatSignedMoney } from '../utils/format'

const store = useLotsStore()
const router = useRouter()

const PERIODS = [
  { v: 'month', label: '月度' },
  { v: 'week', label: '周度' },
  { v: 'day', label: '日度' },
  { v: 'year', label: '年度' }
] as const
const period = ref<'month' | 'week' | 'day' | 'year'>('month')
const refreshing = ref(false)

const periodLabel = computed(() => PERIODS.find(p => p.v === period.value)?.label ?? '月度')

const monthRealized = computed(() => {
  if (period.value !== 'month') return null
  const now = new Date()
  const prefix = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const row = store.pnlRows.find(r => r.key.startsWith(prefix))
  return row?.realizedPnl ?? 0
})

const holdingLots = computed(() => store.lots.filter(l => l.status === 'HOLDING'))

async function loadAll() {
  refreshing.value = true
  try {
    await Promise.all([store.loadSummary(), store.loadPnl(period.value), store.loadLots({ status: 'HOLDING' })])
  } finally {
    refreshing.value = false
  }
}

function switchPeriod(v: 'month' | 'week' | 'day' | 'year') {
  period.value = v
  store.loadPnl(v)
}

onMounted(loadAll)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>仪表盘</h1>
      <button type="button" class="btn" :disabled="refreshing" @click="loadAll">
        {{ refreshing ? '刷新中…' : '刷新数据' }}
      </button>
    </div>

    <div v-if="store.dashError" class="error-banner">
      <span>{{ store.dashError }}</span>
      <button type="button" class="btn btn-sm" @click="loadAll">重试</button>
    </div>

    <div class="cards">
      <div class="card metric">
        <span class="metric-label">持仓成本</span>
        <div v-if="store.loadingSummary" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ formatMoney(store.summary?.holdingCost ?? 0) }}</div>
        <span class="metric-sub">未卖出批次的累计买入成本</span>
      </div>
      <div class="card metric">
        <span class="metric-label">已实现盈亏</span>
        <div v-if="store.loadingSummary" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num" :class="(store.summary?.realizedProfit ?? 0) >= 0 ? 'up' : 'down'">
          {{ formatSignedMoney(store.summary?.realizedProfit ?? 0) }}
        </div>
        <span class="metric-sub">
          已卖出批次的净盈亏（已扣手续费）
          <template v-if="monthRealized !== null">
            · 本月 <b :class="monthRealized >= 0 ? 'up' : 'down'">{{ formatSignedMoney(monthRealized) }}</b>
          </template>
        </span>
      </div>
      <div class="card metric">
        <span class="metric-label">待卖批次</span>
        <div v-if="store.loadingSummary" class="skeleton" style="height:28px;width:60px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ store.summary?.holdingCount ?? 0 }}</div>
        <span class="metric-sub">持有中、可补填卖出的买入记录数</span>
      </div>
    </div>

    <div class="chart-bar">
      <PnlChart :rows="store.pnlRows" :loading="store.loadingPnl" :period-label="periodLabel" />
      <div class="period-group">
        <button
          v-for="p in PERIODS"
          :key="p.v"
          type="button"
          class="chip"
          :class="{ active: period === p.v }"
          @click="switchPeriod(p.v)"
        >{{ p.label }}</button>
      </div>
    </div>

    <h2>当前持仓（待卖出）</h2>
    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th>饰品</th><th>磨损</th><th class="num-head">数量</th><th class="num-head">买入价</th>
            <th class="num-head">买入时间</th><th>买入平台</th><th></th>
          </tr>
        </thead>
        <tbody v-if="store.loading">
          <tr v-for="i in 3" :key="i">
            <td colspan="7"><div class="skeleton" style="height:14px;width:100%"></div></td>
          </tr>
        </tbody>
        <tbody v-else>
          <tr v-for="lot in holdingLots" :key="lot.id">
            <td>{{ lot.itemNameZh ?? lot.itemName }}</td>
            <td>{{ lot.exterior ?? '-' }}</td>
            <td class="num">{{ formatQty(lot.quantity) }}</td>
            <td class="num">{{ formatMoney(lot.buyPrice) }}</td>
            <td class="num mono">{{ formatDateTime(lot.buyTime) }}</td>
            <td><span class="badge badge-muted mono">{{ lot.buyPlatform }}</span></td>
            <td class="row-actions">
              <button type="button" class="btn btn-ghost btn-sm" @click="router.push('/trades')">去补填卖出</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!store.loading && !store.dashError && holdingLots.length === 0" class="empty-state">
      <div class="empty-icon" aria-hidden="true">🎒</div>
      <p>当前没有待卖出的持仓，去饰品账本录入买入。</p>
      <router-link to="/trades" class="btn btn-primary">前往饰品账本</router-link>
    </div>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; }
.page-head h1 { margin-bottom: 16px; }
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 24px; }
.metric { padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
.metric-label { font-size: 12px; font-weight: 550; color: var(--text-secondary); }
.metric-value { font-size: 26px; font-weight: 650; letter-spacing: -.01em; }
.metric-sub { font-size: 12px; color: var(--text-muted); }
.metric-sub b { font-weight: 600; }
.chart-bar { position: relative; }
.period-group { position: absolute; top: 14px; right: 16px; z-index: 2; display: inline-flex; gap: 4px; }
.chip { border: 1px solid var(--border); background: var(--surface); color: var(--text-secondary); border-radius: 999px; padding: 3px 10px; font-size: 12px; cursor: pointer; transition: background var(--motion-fast) ease, color var(--motion-fast) ease, border-color var(--motion-fast) ease; }
.chip:hover { border-color: var(--border-strong); color: var(--text); }
.chip.active { background: var(--accent-soft); color: var(--accent); border-color: var(--accent); font-weight: 550; }
table.data th.num-head, table.data td.num { text-align: right; font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
.row-actions { text-align: right; white-space: nowrap; }
@media (max-width: 760px) {
  .cards { grid-template-columns: 1fr; }
  .period-group { position: static; margin: -8px 0 12px; }
}
</style>