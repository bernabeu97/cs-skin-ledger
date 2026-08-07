<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PnlChart from '../components/PnlChart.vue'
import { useLotsStore } from '../stores/lots'
import { useAlertsStore } from '../stores/alerts'
import ItemSelect from '../components/ItemSelect.vue'
import { formatDateTime, formatMoney, formatQty, formatSignedMoney } from '../utils/format'
import type { HoldingValuation, Item } from '../types'

const store = useLotsStore()
const alertsStore = useAlertsStore()
const router = useRouter()

const PERIODS = [
  { v: 'month', label: '月度' },
  { v: 'week', label: '周度' },
  { v: 'day', label: '日度' },
  { v: 'year', label: '年度' }
] as const
const period = ref<'month' | 'week' | 'day' | 'year'>('month')
const refreshing = ref(false)
const refreshingPrices = ref(false)
const priceMessage = ref('')
const alertItem = ref<Item | null>(null)
const alertPlatform = ref('uu')
const alertCondition = ref<'gt' | 'lt'>('gt')
const alertThreshold = ref<number | null>(null)
const alertBusy = ref(false)

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
      alertsStore.loadAlerts()
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
    const result = await store.refreshPrices()
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

async function addAlert() {
  if (!alertItem.value || alertThreshold.value == null || !(alertThreshold.value > 0)) return
  alertBusy.value = true
  try {
    await alertsStore.createAlert({
      itemId: alertItem.value.id,
      platform: alertPlatform.value,
      condition: alertCondition.value,
      threshold: alertThreshold.value
    })
    alertItem.value = null
    alertThreshold.value = null
    priceMessage.value = '价格提醒已添加'
  } catch (e) {
    priceMessage.value = '添加提醒失败：' + String(e)
  } finally {
    alertBusy.value = false
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

    <div v-if="priceMessage" class="hint-banner ok">
      <span>{{ priceMessage }}</span>
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
      <div class="card metric">
        <span class="metric-label">当前市值</span>
        <div v-if="store.loadingValuation" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ formatMoney(store.valuation?.marketValue ?? 0) }}</div>
        <span class="metric-sub">
          按最新行情价 × 数量
          <template v-if="store.valuation?.priceAsOf">· {{ formatDateTime(store.valuation.priceAsOf) }}</template>
        </span>
      </div>
      <div class="card metric">
        <span class="metric-label">浮动盈亏</span>
        <div v-if="store.loadingValuation" class="skeleton" style="height:28px;width:120px;margin-top:8px"></div>
        <div v-else class="metric-value num" :class="(store.valuation?.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">
          {{ formatSignedMoney(store.valuation?.unrealizedPnl ?? 0) }}
        </div>
        <span class="metric-sub">当前市值 − 持仓成本（行情优先 UU）</span>
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
            <th class="num-head">UU 价</th><th class="num-head">Steam 价</th><th class="num-head">当前价</th>
            <th class="num-head">浮动盈亏</th><th class="num-head">买入时间</th><th>买入平台</th><th></th>
          </tr>
        </thead>
        <tbody v-if="store.loading">
          <tr v-for="i in 3" :key="i">
            <td colspan="11"><div class="skeleton" style="height:14px;width:100%"></div></td>
          </tr>
        </tbody>
        <tbody v-else>
          <tr v-for="lot in holdingLots" :key="lot.id">
            <td>{{ lot.itemNameZh ?? lot.itemName }}</td>
            <td>{{ lot.exterior ?? '-' }}</td>
            <td class="num">{{ formatQty(lot.quantity) }}</td>
            <td class="num">{{ formatMoney(lot.buyPrice) }}</td>
            <template v-if="valuationMap.get(lot.id)">
              <td class="num">{{ valuationMap.get(lot.id)?.latestPrices.uu != null ? formatMoney(valuationMap.get(lot.id)!.latestPrices.uu) : '-' }}</td>
              <td class="num">{{ valuationMap.get(lot.id)?.latestPrices.steam != null ? formatMoney(valuationMap.get(lot.id)!.latestPrices.steam) : '-' }}</td>
              <td class="num">
                <template v-if="valuationMap.get(lot.id)?.currentPrice != null">
                  {{ formatMoney(valuationMap.get(lot.id)!.currentPrice!) }}
                  <span class="badge badge-muted mono">{{ valuationMap.get(lot.id)?.pricePlatform }}</span>
                </template>
                <span v-else class="text-muted">-</span>
              </td>
              <td class="num" :class="(valuationMap.get(lot.id)?.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">
                {{ valuationMap.get(lot.id)?.unrealizedPnl != null ? formatSignedMoney(valuationMap.get(lot.id)!.unrealizedPnl!) : '-' }}
              </td>
            </template>
            <template v-else>
              <td class="num text-muted" colspan="4">暂无行情</td>
            </template>
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
    <h2>价格提醒</h2>
    <div class="alert-bar">
      <ItemSelect v-model="alertItem" placeholder="搜索要提醒的饰品（支持中文）" />
      <select v-model="alertPlatform" class="input" style="width:auto">
        <option value="uu">UU</option>
        <option value="steam">Steam</option>
        <option value="buff">BUFF</option>
      </select>
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
    <div v-if="alertsStore.error" class="error-banner">
      <span>{{ alertsStore.error }}</span>
    </div>
    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th>饰品</th><th>平台</th><th>条件</th><th class="num-head">阈值</th><th>状态</th><th></th>
          </tr>
        </thead>
        <tbody v-if="alertsStore.loading">
          <tr><td colspan="6"><div class="skeleton" style="height:14px;width:100%"></div></td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="a in alertsStore.alerts" :key="a.id">
            <td>{{ a.itemNameZh ?? a.itemName }}</td>
            <td><span class="badge badge-muted mono">{{ a.platform }}</span></td>
            <td>{{ a.condition === 'gt' ? '价格高于' : '价格低于' }}</td>
            <td class="num">{{ formatMoney(a.threshold) }}</td>
            <td>
              <span v-if="a.triggeredAt" class="badge badge-accent">已触发 {{ formatDateTime(a.triggeredAt) }}</span>
              <span v-else class="badge badge-muted">监控中</span>
            </td>
            <td class="row-actions">
              <button type="button" class="btn btn-ghost btn-sm" @click="alertsStore.resetAlert(a.id)">重置</button>
              <button type="button" class="btn btn-ghost btn-sm danger-text" @click="alertsStore.deleteAlert(a.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="!alertsStore.loading && alertsStore.alerts.length === 0" class="empty-state compact">
      <p>还没有价格提醒。添加后，每次“刷新行情”会自动检查是否触发。</p>
    </div>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.page-head h1 { margin-bottom: 16px; }
.head-actions { display: inline-flex; gap: 8px; margin-bottom: 16px; }
.cards { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; margin-bottom: 24px; }
.metric { padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
.metric-label { font-size: 12px; font-weight: 550; color: var(--text-secondary); }
.metric-value { font-size: 26px; font-weight: 650; letter-spacing: -.01em; }
.metric-sub { font-size: 12px; color: var(--text-muted); }
.metric-sub b { font-weight: 600; }
.hint-banner { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; margin-bottom: 14px; border-radius: var(--radius); background: var(--accent-soft); border: 1px solid #c7d9fb; color: var(--text-secondary); font-size: 13px; }
.hint-banner.ok { background: var(--success-soft); border-color: #b8e3c6; }
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
.alert-bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.alert-bar .item-select { flex: 1; min-width: 220px; }
.danger-text { color: var(--danger) !important; }
.empty-state.compact { padding: 12px; }
@media (max-width: 1100px) {
  .cards { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 760px) {
  .cards { grid-template-columns: 1fr; }
  .period-group { position: static; margin: -8px 0 12px; }
  .hint-banner { flex-direction: column; align-items: flex-start; }
}
</style>