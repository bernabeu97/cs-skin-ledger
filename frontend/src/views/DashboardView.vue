<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PnlChart from '../components/PnlChart.vue'
import { useTradesStore } from '../stores/trades'
import { formatMoney, formatQty, formatSignedMoney } from '../utils/format'

const store = useTradesStore()
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

async function loadAll() {
  refreshing.value = true
  try {
    await Promise.all([store.loadPortfolio(), store.loadPnl(period.value)])
  } finally {
    refreshing.value = false
  }
}

function switchPeriod(v: 'month' | 'week' | 'day' | 'year') {
  period.value = v
  store.loadPnl(v)
}

function goTrades(itemName: string) {
  router.push({ path: '/trades', query: { q: itemName } })
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
        <span class="metric-label">持仓总成本</span>
        <div v-if="store.loadingPortfolio" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ formatMoney(store.totalCost) }}</div>
        <span class="metric-sub">剩余持仓的累计买入成本（含手续费）</span>
      </div>
      <div class="card metric">
        <span class="metric-label">已实现盈亏</span>
        <div v-if="store.loadingPortfolio" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num" :class="store.totalRealizedPnl >= 0 ? 'up' : 'down'">
          {{ formatSignedMoney(store.totalRealizedPnl) }}
        </div>
        <span class="metric-sub">
          累计已平仓净盈亏
          <template v-if="monthRealized !== null">
            · 本月 <b :class="monthRealized >= 0 ? 'up' : 'down'">{{ formatSignedMoney(monthRealized) }}</b>
          </template>
        </span>
      </div>
      <div class="card metric">
        <span class="metric-label">当前持仓</span>
        <div v-if="store.loadingPortfolio" class="skeleton" style="height:28px;width:60px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ store.holdings.length }}</div>
        <span class="metric-sub">当前持有数量的饰品品种数</span>
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

    <h2>当前持仓</h2>
    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th>饰品</th><th class="num-head">数量</th><th class="num-head">平均成本</th>
            <th class="num-head">已实现盈亏</th><th class="num-head">浮动盈亏</th><th></th>
          </tr>
        </thead>
        <tbody v-if="store.loadingPortfolio">
          <tr v-for="i in 3" :key="i">
            <td colspan="6"><div class="skeleton" style="height:14px;width:100%"></div></td>
          </tr>
        </tbody>
        <tbody v-else>
          <tr v-for="h in store.holdings" :key="h.itemName">
            <td>{{ h.itemName }}</td>
            <td class="num">{{ formatQty(h.quantity) }}</td>
            <td class="num">{{ formatMoney(h.avgCost) }}</td>
            <td class="num" :class="h.realizedPnl >= 0 ? 'up' : 'down'">{{ formatSignedMoney(h.realizedPnl) }}</td>
            <td class="num">{{ h.unrealizedPnl == null ? '-' : formatMoney(h.unrealizedPnl) }}</td>
            <td class="row-actions">
              <button type="button" class="btn btn-ghost btn-sm" @click="goTrades(h.itemName)">查看交易</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!store.loadingPortfolio && !store.dashError && store.holdings.length === 0" class="empty-state">
      <div class="empty-icon" aria-hidden="true">🎒</div>
      <p>当前没有任何持仓，去交易记录页录入买卖即可生成持仓与盈亏。</p>
      <router-link to="/trades" class="btn btn-primary">前往交易记录</router-link>
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
.period-group {
  position: absolute; top: 14px; right: 16px; z-index: 2;
  display: inline-flex; gap: 4px;
}
.chip {
  border: 1px solid var(--border); background: var(--surface); color: var(--text-secondary);
  border-radius: 999px; padding: 3px 10px; font-size: 12px; cursor: pointer;
  transition: background var(--motion-fast) ease, color var(--motion-fast) ease, border-color var(--motion-fast) ease;
}
.chip:hover { border-color: var(--border-strong); color: var(--text); }
.chip.active { background: var(--accent-soft); color: var(--accent); border-color: var(--accent); font-weight: 550; }
table.data th.num-head, table.data td.num { text-align: right; font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
.row-actions { text-align: right; white-space: nowrap; }
@media (max-width: 760px) {
  .cards { grid-template-columns: 1fr; }
  .period-group { position: static; margin: -8px 0 12px; }
}
</style>