<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import ColumnPicker from '../components/ColumnPicker.vue'
import PnlChart from '../components/PnlChart.vue'
import { useLotsStore } from '../stores/lots'
import { useCostsStore } from '../stores/costs'
import { formatDateTime, formatMoney, formatQty, formatSignedMoney } from '../utils/format'
import { useColumnVisibility } from '../utils/columnVisibility'
import type { HoldingValuation } from '../types'

const store = useLotsStore()
const costsStore = useCostsStore()
const router = useRouter()

const PERIODS = [
  { v: 'month', label: '月度' },
  { v: 'week', label: '周度' },
  { v: 'day', label: '日度' },
  { v: 'year', label: '年度' }
] as const
const HOLDING_COLUMNS = [
  { key: 'item', label: '饰品' }, { key: 'exterior', label: '磨损' }, { key: 'quantity', label: '数量' },
  { key: 'buyPrice', label: '买入价' }, { key: 'currentPrice', label: '当前价（UU）' },
  { key: 'unrealizedPnl', label: '浮动盈亏' }, { key: 'buyTime', label: '买入时间' }, { key: 'buyPlatform', label: '买入平台' }
]
const period = ref<'month' | 'week' | 'day' | 'year'>('month')
const refreshing = ref(false)
const refreshingPrices = ref(false)
const priceMessage = ref('')
const { visibleColumns: holdingVisibleColumns, isColumnVisible: isHoldingColumnVisible } = useColumnVisibility('columns:dashboard-holdings', HOLDING_COLUMNS)

const periodLabel = computed(() => PERIODS.find(p => p.v === period.value)?.label ?? '月度')

const monthRealized = computed(() => {
  if (period.value !== 'month') return null
  const now = new Date()
  const prefix = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const row = store.pnlRows.find(r => r.key.startsWith(prefix))
  return row?.realizedPnl ?? 0
})

const holdingLots = computed(() => store.lots.filter(l => l.status === 'HOLDING'))

/** lotId -> 估值行 */
const valuationMap = computed(() => {
  const map = new Map<number, HoldingValuation>()
  for (const v of store.valuation?.rows ?? []) map.set(v.lotId, v)
  return map
})

const pricedCount = computed(() => (store.valuation?.rows ?? []).filter(r => r.currentPrice != null).length)
const holdingCount = computed(() => store.summary?.holdingCount ?? 0)
const hasAnyPrice = computed(() => pricedCount.value > 0)
const pendingCount = computed(() => store.lots.filter(l => l.status === 'HOLDING' && l.buyPrice === 0).length)
const marketValueText = computed(() => (holdingCount.value > 0 && !hasAnyPrice.value) ? '-' : formatMoney(store.valuation?.marketValue ?? 0))
const unrealizedText = computed(() => (holdingCount.value > 0 && !hasAnyPrice.value) ? '-' : formatSignedMoney(store.valuation?.unrealizedPnl ?? 0))
const priceMessageIsWarning = computed(() => priceMessage.value.includes('未填写磨损等级'))

/** 总盈亏 = 饰品已实现盈亏 + 其他收支净额 */
const totalPnl = computed(() => (store.summary?.realizedProfit ?? 0) + (costsStore.summary?.net ?? 0))
const totalPnlText = computed(() => (holdingCount.value > 0 && !hasAnyPrice.value) ? '-' : formatSignedMoney(totalPnl.value))
const totalRoi = computed(() => {
  const invested = store.summary?.totalBuyCost ?? 0
  if (invested <= 0) return null
  const pnl = (store.summary?.realizedProfit ?? 0) + (store.valuation?.unrealizedPnl ?? 0)
  return pnl / invested
})
const realizedRoi = computed(() => {
  const invested = store.summary?.totalBuyCost ?? 0
  if (invested <= 0) return null
  return (store.summary?.realizedProfit ?? 0) / invested
})
const winRate = computed(() => store.stats?.winRate ?? null)
const topItems = computed(() => (store.aggregate ?? []).slice(0, 10))

function percent(v: number | null | undefined): string {
  if (v == null || Number.isNaN(v)) return '-'
  return `${v >= 0 ? '+' : ''}${(v * 100).toFixed(2)}%`
}

const priceHint = computed(() => {
  if (store.priceConfig?.csqaqConfigured) return ''
  const msg = store.priceConfig?.messages?.csqaq
  return msg || '未配置行情数据源，浮动盈亏暂不可用。'
})

async function loadAll() {
  refreshing.value = true
  try {
    await Promise.all([
      store.loadSummary(),
      store.loadPnl(period.value),
      store.loadLots({ status: 'HOLDING' }),
      store.loadValuation(),
      store.loadPriceConfig(),
      costsStore.loadSummary(),
      store.loadStats(),
      store.loadAggregate('item')
    ])
  } finally {
    refreshing.value = false
  }
}

function switchPeriod(v: 'month' | 'week' | 'day' | 'year') {
  period.value = v
  store.loadPnl(v)
}

async function refreshPrices() {
  refreshingPrices.value = true
  priceMessage.value = ''
  try {
    const result = await store.refreshPrices('uu')
    const parts = Object.entries(result.byPlatform)
      .filter(([, n]) => n > 0)
      .map(([p, n]) => `${p} ${n} 条`)
    priceMessage.value = parts.length
      ? `行情刷新完成：${parts.join('，')}`
      : '本次未获取到价格（可能未配置数据源或请求被限流）'
    if (result.errors.length) {
      priceMessage.value += `；${result.errors[0]}`
    }
  } catch (e) {
    priceMessage.value = String(e)
  } finally {
    refreshingPrices.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>仪表盘</h1>
      <div class="head-actions">
        <button type="button" class="btn" :disabled="refreshingPrices || refreshing" @click="refreshPrices">
          {{ refreshingPrices ? '刷新行情中…' : '刷新行情' }}
        </button>
        <button type="button" class="btn" :disabled="refreshing" @click="loadAll">
          {{ refreshing ? '刷新中…' : '刷新数据' }}
        </button>
      </div>
    </div>

    <div v-if="priceHint" class="hint-banner">
      <span>{{ priceHint }}</span>
      <button type="button" class="btn btn-sm" @click="refreshPrices">仍要刷新</button>
    </div>

    <div v-if="pendingCount > 0" class="hint-banner warn">
      <span>{{ pendingCount }} 条持仓缺少买入价，浮动盈亏可能不准确，建议尽快补填。</span>
      <button type="button" class="btn btn-sm" @click="router.push('/trades?pending=1')">去补填</button>
    </div>

    <div v-if="priceMessage" class="hint-banner" :class="priceMessageIsWarning ? 'warn' : 'ok'">
      <span>{{ priceMessage }}</span>
    </div>

    <div v-if="store.dashError" class="error-banner">
      <span>{{ store.dashError }}</span>
      <button type="button" class="btn btn-sm" @click="loadAll">重试</button>
    </div>

    <div class="cards">
      <div class="card metric metric-primary">
        <span class="metric-label">总盈亏（已实现 + 其他收支）</span>
        <div v-if="store.loadingSummary" class="skeleton" style="height:28px;width:140px;margin-top:8px"></div>
        <div v-else class="metric-value num" :class="totalPnl >= 0 ? 'up' : 'down'">{{ totalPnlText }}</div>
        <span class="metric-sub">
          已实现 {{ formatSignedMoney(store.summary?.realizedProfit ?? 0) }} · 其他收支 {{ formatSignedMoney(costsStore.summary?.net ?? 0) }}
          <template v-if="monthRealized !== null">· 本月 <b :class="monthRealized >= 0 ? 'up' : 'down'">{{ formatSignedMoney(monthRealized) }}</b></template>
        </span>
      </div>
      <div class="card metric">
        <span class="metric-label">持仓成本</span>
        <div v-if="store.loadingSummary" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ formatMoney(store.summary?.holdingCost ?? 0) }}</div>
        <span class="metric-sub">未卖出批次的累计买入成本</span>
      </div>
      <div class="card metric">
        <span class="metric-label">待卖批次</span>
        <div v-if="store.loadingSummary" class="skeleton" style="height:28px;width:60px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ store.summary?.holdingCount ?? 0 }}</div>
        <span class="metric-sub">持有中、可补填卖出的买入记录数</span>
      </div>
      <div class="card metric">
        <span class="metric-label">当前市值</span>
        <div v-if="store.loadingValuation" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ marketValueText }}</div>
        <span class="metric-sub">
          按 UU 最新价 × 数量
          <template v-if="store.valuation?.priceAsOf">· {{ formatDateTime(store.valuation.priceAsOf) }}</template>
        </span>
      </div>
      <div class="card metric">
        <span class="metric-label">其他收支净额</span>
        <div class="metric-value num" :class="(costsStore.summary?.net ?? 0) >= 0 ? 'up' : 'down'">
          {{ formatSignedMoney(costsStore.summary?.net ?? 0) }}
        </div>
        <span class="metric-sub">会员费 / 赔偿等非饰品收支（收入 {{ formatMoney(costsStore.summary?.totalIncome ?? 0) }} · 支出 {{ formatMoney(costsStore.summary?.totalExpense ?? 0) }}）</span>
      </div>
      <div class="card metric">
        <span class="metric-label">浮动盈亏</span>
        <div v-if="store.loadingValuation" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num" :class="(store.valuation?.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">
          {{ unrealizedText }}
        </div>
        <span class="metric-sub">当前市值 − 持仓成本（统一 UU 价）</span>
      </div>
      <div class="card metric">
        <span class="metric-label">总 ROI</span>
        <div class="metric-value num" :class="(totalRoi ?? 0) >= 0 ? 'up' : 'down'">{{ percent(totalRoi) }}</div>
        <span class="metric-sub">(已实现 + 浮动) ÷ 总买入成本</span>
      </div>
      <div class="card metric">
        <span class="metric-label">已实现 ROI</span>
        <div class="metric-value num" :class="(realizedRoi ?? 0) >= 0 ? 'up' : 'down'">{{ percent(realizedRoi) }}</div>
        <span class="metric-sub">已实现盈亏 ÷ 总买入成本</span>
      </div>
      <div class="card metric">
        <span class="metric-label">胜率</span>
        <div class="metric-value num">{{ winRate == null ? '-' : Math.round(winRate * 100) + '%' }}</div>
        <span class="metric-sub">盈利卖出批次 ÷ 已卖出批次{{ store.stats ? `（${store.stats.winningSoldCount}/${store.stats.soldCount}）` : '' }}</span>
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

    <div class="section-head">
      <h2>当前持仓（待卖出）</h2>
      <ColumnPicker v-model="holdingVisibleColumns" :columns="HOLDING_COLUMNS" />
    </div>
    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th v-if="isHoldingColumnVisible('item')">饰品</th>
            <th v-if="isHoldingColumnVisible('exterior')">磨损</th>
            <th v-if="isHoldingColumnVisible('quantity')" class="num-head">数量</th>
            <th v-if="isHoldingColumnVisible('buyPrice')" class="num-head">买入价</th>
            <th v-if="isHoldingColumnVisible('currentPrice')" class="num-head">当前价(UU)</th>
            <th v-if="isHoldingColumnVisible('unrealizedPnl')" class="num-head">浮动盈亏</th>
            <th v-if="isHoldingColumnVisible('buyTime')" class="num-head">买入时间</th>
            <th v-if="isHoldingColumnVisible('buyPlatform')">买入平台</th><th></th>
          </tr>
        </thead>
        <tbody v-if="store.loading">
          <tr v-for="i in 3" :key="i">
            <td :colspan="holdingVisibleColumns.length + 1"><div class="skeleton" style="height:14px;width:100%"></div></td>
          </tr>
        </tbody>
        <tbody v-else>
          <tr v-for="lot in holdingLots" :key="lot.id">
            <td v-if="isHoldingColumnVisible('item')">{{ lot.itemNameZh ?? lot.itemName }}</td>
            <td v-if="isHoldingColumnVisible('exterior')">{{ lot.exterior ?? '-' }}</td>
            <td v-if="isHoldingColumnVisible('quantity')" class="num">{{ formatQty(lot.quantity) }}</td>
            <td v-if="isHoldingColumnVisible('buyPrice')" class="num">{{ formatMoney(lot.buyPrice) }}</td>
            <td v-if="isHoldingColumnVisible('currentPrice')" class="num">
                <template v-if="valuationMap.get(lot.id)?.currentPrice != null">
                  {{ formatMoney(valuationMap.get(lot.id)!.currentPrice!) }}
                  <span class="badge badge-muted mono">{{ valuationMap.get(lot.id)?.pricePlatform }}</span>
                </template>
                <span v-else class="text-muted">-</span>
            </td>
            <td v-if="isHoldingColumnVisible('unrealizedPnl')" class="num" :class="(valuationMap.get(lot.id)?.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">
              {{ valuationMap.get(lot.id)?.unrealizedPnl != null ? formatSignedMoney(valuationMap.get(lot.id)!.unrealizedPnl!) : '-' }}
            </td>
            <td v-if="isHoldingColumnVisible('buyTime')" class="num mono">{{ formatDateTime(lot.buyTime) }}</td>
            <td v-if="isHoldingColumnVisible('buyPlatform')"><span class="badge badge-muted mono">{{ lot.buyPlatform }}</span></td>
            <td class="row-actions">
              <button type="button" class="btn btn-ghost btn-sm" @click="router.push('/trades?lotId=' + lot.id)">去补填卖出</button>
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

    <div class="section-head top-head">
      <h2>单品盈亏 TOP10</h2>
      <router-link to="/trades" class="top-more">账本分组汇总 →</router-link>
    </div>
    <div class="table-wrap">
      <table class="data">
        <thead><tr><th>饰品</th><th class="num-head">已实现盈亏</th><th class="num-head">买入成本</th><th class="num-head">卖出笔数</th><th class="num-head">胜率</th></tr></thead>
        <tbody v-if="store.loading">
          <tr><td colspan="5"><div class="skeleton" style="height:14px;width:100%"></div></td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="row in topItems" :key="row.key">
            <td class="top-name" :title="row.key">{{ row.key }}</td>
            <td class="num" :class="row.realizedPnl >= 0 ? 'up' : 'down'">{{ formatSignedMoney(row.realizedPnl) }}</td>
            <td class="num">{{ formatMoney(row.buyCost) }}</td>
            <td class="num">{{ row.soldCount }}</td>
            <td class="num">{{ row.soldCount ? Math.round((row.winningSoldCount / row.soldCount) * 100) + '%' : '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!store.loading && topItems.length === 0" class="empty-state compact">
      <p>还没有已卖出的记录，卖出后会在这里看到单品盈亏排行。</p>
    </div>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.page-head h1 { margin-bottom: 16px; }
.head-actions { display: inline-flex; gap: 8px; margin-bottom: 16px; }
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 24px; }
.metric { padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
.metric-primary { background: linear-gradient(135deg, var(--surface) 0%, var(--accent-soft) 100%); border-color: var(--accent-glow); }
.metric-label { font-size: 12px; font-weight: 550; color: var(--text-secondary); }
.metric-value { font-size: 26px; font-weight: 650; letter-spacing: -.01em; }
.metric-sub { font-size: 12px; color: var(--text-muted); }
.metric-sub b { font-weight: 600; }
.up { color: var(--success); }
.down { color: var(--danger); }
.hint-banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; margin-bottom: 14px; border-radius: var(--radius); background: var(--accent-soft); border: 1px solid var(--accent-glow); color: var(--text-secondary); font-size: 13px; }
.hint-banner.ok { background: var(--success-soft); border-color: var(--success); }
.hint-banner.warn { background: var(--warn-bg); border-color: var(--warn-border); color: var(--warn-text); }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.section-head h2 { margin: 0; }
.chart-bar { position: relative; }
.period-group { position: absolute; top: 14px; right: 16px; z-index: 2; display: inline-flex; gap: 4px; }
.chip { border: 1px solid var(--border); background: var(--surface); color: var(--text-secondary); border-radius: 999px; padding: 3px 10px; font-size: 12px; cursor: pointer; transition: background var(--motion-fast) ease, color var(--motion-fast) ease, border-color var(--motion-fast) ease; }
.chip:hover { border-color: var(--border-strong); color: var(--text); }
.chip.active { background: var(--accent-soft); color: var(--accent); border-color: var(--accent); font-weight: 550; }
table.data th.num-head, table.data td.num { text-align: right; font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
table.data td.up { color: var(--success); font-weight: 600; }
table.data td.down { color: var(--danger); font-weight: 600; }
.text-muted { color: var(--text-muted); }
.row-actions { text-align: right; white-space: nowrap; }
.top-head { margin-top: 26px; }
.top-more { font-size: 12px; }
.top-name { max-width: 360px; overflow: hidden; text-overflow: ellipsis; }
.empty-state.compact { padding: 18px; }
@media (max-width: 1100px) {
  .cards { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 760px) {
  .cards { grid-template-columns: 1fr; }
  .period-group { position: static; margin: -8px 0 12px; }
  .hint-banner { flex-direction: column; align-items: flex-start; }
}
</style>
