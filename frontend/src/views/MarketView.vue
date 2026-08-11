<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import ItemSelect from '../components/ItemSelect.vue'
import MarketLineChart from '../components/MarketLineChart.vue'
import { errorMessage } from '../api/client'
import { useLotsStore } from '../stores/lots'
import { useMarketStore, type IndexKind, type MarketPeriod } from '../stores/market'
import { useUiStore } from '../stores/ui'
import type { Item, WatchlistItem } from '../types'
import { formatDateTime, formatMoney, formatSignedMoney } from '../utils/format'

const lots = useLotsStore()
const market = useMarketStore()
const ui = useUiStore()

const tab = ref<'holdings' | 'watchlist' | 'market'>('holdings')
const period = ref<MarketPeriod>('24h')
const selectedItem = ref<Item | null>(null)
const selectedExterior = ref('')
const selectedWatch = ref<WatchlistItem | null>(null)
const adding = ref(false)
const refreshing = ref(false)

const periods: Array<{ value: MarketPeriod; label: string }> = [
  { value: '24h', label: '24小时' },
  { value: '7d', label: '7天' },
  { value: '30d', label: '30天' },
  { value: '90d', label: '90天' }
]

const indexKind = computed<IndexKind>(() => tab.value === 'watchlist' ? 'watchlist' : 'holdings')
const indexTitle = computed(() => tab.value === 'watchlist' ? '自选组合指数' : '持仓指数')
const indexSubtitle = computed(() => tab.value === 'watchlist'
  ? '基准 100 · 等权计算 · 成分变化采用链式衔接 · UU 价'
  : '基准 100 · 按持仓市值加权 · 买卖资金变化不计入涨跌 · UU 价')

async function load() {
  await Promise.all([market.loadWatchlist(), lots.loadValuation(), market.loadIndex(indexKind.value, period.value)])
  if (!selectedWatch.value && market.watchlist.length) {
    selectedWatch.value = market.watchlist[0]
  }
  if (tab.value === 'watchlist' && selectedWatch.value) {
    await market.loadHistory(selectedWatch.value.itemId, selectedWatch.value.exterior, period.value)
  }
}

async function switchTab(next: typeof tab.value) {
  tab.value = next
  if (next === 'market') return
  await market.loadIndex(indexKind.value, period.value)
  if (next === 'watchlist' && selectedWatch.value) {
    await market.loadHistory(selectedWatch.value.itemId, selectedWatch.value.exterior, period.value)
  }
}

async function switchPeriod(next: MarketPeriod) {
  period.value = next
  if (tab.value === 'market') return
  await market.loadIndex(indexKind.value, next)
  if (tab.value === 'watchlist' && selectedWatch.value) {
    await market.loadHistory(selectedWatch.value.itemId, selectedWatch.value.exterior, next)
  }
}

async function selectWatch(entry: WatchlistItem) {
  selectedWatch.value = entry
  await market.loadHistory(entry.itemId, entry.exterior, period.value)
}

async function addWatch() {
  if (!selectedItem.value || adding.value) return
  adding.value = true
  try {
    await market.addWatch(selectedItem.value.id, selectedExterior.value)
    selectedItem.value = null
    selectedExterior.value = ''
    ui.toast('success', '已加入自选，下一次刷新行情会同步 UU 价格')
    await market.loadIndex('watchlist', period.value)
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    adding.value = false
  }
}

async function deleteWatch(entry: WatchlistItem) {
  try {
    await market.deleteWatch(entry.id)
    if (selectedWatch.value?.id === entry.id) {
      selectedWatch.value = market.watchlist[0] ?? null
      market.history = null
    }
    await market.loadIndex('watchlist', period.value)
    ui.toast('success', '已移出自选')
  } catch (e) {
    ui.toast('error', errorMessage(e))
  }
}

async function refreshPrices() {
  if (refreshing.value) return
  refreshing.value = true
  try {
    const result = await lots.refreshPrices('uu')
    await load()
    for (const alert of result.triggeredAlerts ?? []) {
      ui.toast('info', `${alert.itemNameZh ?? alert.itemName}${alert.exterior ? `（${alert.exterior}）` : ''} 已达到提醒价`, 8000)
    }
    ui.toast(result.ok > 0 ? 'success' : 'info', result.ok > 0 ? `已更新 ${result.ok} 条 UU 行情` : '本次没有获取到新行情')
  } catch (e) {
    ui.toast('error', errorMessage(e))
  } finally {
    refreshing.value = false
  }
}

watch(selectedItem, () => { selectedExterior.value = '' })
onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <h1>行情盯盘</h1>
        <p>当前市值、趋势和提醒统一使用悠悠有品（UU）市场价格。</p>
      </div>
      <button type="button" class="btn btn-primary" :disabled="refreshing" @click="refreshPrices">
        {{ refreshing ? '刷新中…' : '刷新 UU 行情' }}
      </button>
    </div>

    <div class="market-tabs" role="tablist" aria-label="行情范围">
      <button type="button" :class="{ active: tab === 'holdings' }" @click="switchTab('holdings')">持仓</button>
      <button type="button" :class="{ active: tab === 'watchlist' }" @click="switchTab('watchlist')">自选</button>
      <button type="button" :class="{ active: tab === 'market' }" @click="switchTab('market')">
        大盘 <span class="pending-dot">待接入</span>
      </button>
      <div v-if="tab !== 'market'" class="periods">
        <button
          v-for="item in periods"
          :key="item.value"
          type="button"
          :class="{ active: period === item.value }"
          @click="switchPeriod(item.value)"
        >{{ item.label }}</button>
      </div>
    </div>

    <div v-if="market.error" class="error-banner">
      <span>{{ market.error }}</span>
      <button type="button" class="btn btn-sm" @click="load">重试</button>
    </div>

    <template v-if="tab === 'holdings'">
      <div class="metrics">
        <div class="metric-block">
          <span>当前市值</span>
          <strong class="num">{{ formatMoney(lots.valuation?.marketValue ?? 0) }}</strong>
          <small>{{ lots.valuation?.priceAsOf ? formatDateTime(lots.valuation.priceAsOf) : '暂无行情时间' }}</small>
        </div>
        <div class="metric-block">
          <span>浮动盈亏</span>
          <strong class="num" :class="(lots.valuation?.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">
            {{ formatSignedMoney(lots.valuation?.unrealizedPnl ?? 0) }}
          </strong>
          <small>当前市值 − 持仓成本</small>
        </div>
        <div class="metric-block">
          <span>{{ period }} 指数涨跌</span>
          <strong class="num" :class="(market.index?.changePercent ?? 0) >= 0 ? 'up' : 'down'">
            {{ market.index?.changePercent == null ? '-' : `${market.index.changePercent >= 0 ? '+' : ''}${market.index.changePercent.toFixed(2)}%` }}
          </strong>
          <small>成分变化不会造成跳点</small>
        </div>
      </div>

      <MarketLineChart
        :title="indexTitle"
        :subtitle="indexSubtitle"
        :points="market.index?.points ?? []"
        :loading="market.loading"
        value-type="index"
      />

      <section class="list-section">
        <div class="section-head"><h2>持仓明细</h2><span>{{ lots.valuation?.rows.length ?? 0 }} 条</span></div>
        <div class="table-wrap">
          <table class="data">
            <thead><tr><th>饰品</th><th>磨损</th><th class="num-head">数量</th><th class="num-head">买入价</th><th class="num-head">UU价</th><th class="num-head">浮动盈亏</th></tr></thead>
            <tbody>
              <tr v-for="row in lots.valuation?.rows ?? []" :key="row.lotId">
                <td>{{ row.itemNameZh ?? row.itemName }}</td>
                <td>{{ row.exterior ?? '-' }}</td>
                <td class="num">{{ row.quantity }}</td>
                <td class="num">{{ formatMoney(row.buyPrice) }}</td>
                <td class="num">{{ row.currentPrice == null ? '-' : formatMoney(row.currentPrice) }}</td>
                <td class="num" :class="(row.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">{{ row.unrealizedPnl == null ? '-' : formatSignedMoney(row.unrealizedPnl) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="!lots.loadingValuation && (lots.valuation?.rows.length ?? 0) === 0" class="empty-state compact"><p>暂无持仓记录。</p></div>
      </section>
    </template>

    <template v-else-if="tab === 'watchlist'">
      <section class="card add-watch">
        <div>
          <h2>添加关注饰品</h2>
          <p>最多50个饰品与磨损组合；行情只采用 UU 价。</p>
        </div>
        <ItemSelect v-model="selectedItem" placeholder="搜索饰品中文名称" />
        <select v-model="selectedExterior" class="select wear-select" :disabled="!selectedItem">
          <option value="">无磨损 / 不区分</option>
          <option v-for="wear in selectedItem?.wears ?? []" :key="wear" :value="wear">{{ wear }}</option>
        </select>
        <button type="button" class="btn btn-primary" :disabled="!selectedItem || adding" @click="addWatch">{{ adding ? '添加中…' : '加入自选' }}</button>
      </section>

      <div class="metrics compact-metrics">
        <div class="metric-block">
          <span>自选数量</span><strong class="num">{{ market.watchlist.length }} / 50</strong><small>按饰品＋磨损计数</small>
        </div>
        <div class="metric-block">
          <span>组合指数</span><strong class="num">{{ market.index?.currentValue?.toFixed(2) ?? '-' }}</strong><small>等权 · 基准100</small>
        </div>
        <div class="metric-block">
          <span>{{ period }} 涨跌</span>
          <strong class="num" :class="(market.index?.changePercent ?? 0) >= 0 ? 'up' : 'down'">{{ market.index?.changePercent == null ? '-' : `${market.index.changePercent >= 0 ? '+' : ''}${market.index.changePercent.toFixed(2)}%` }}</strong>
          <small>{{ market.index?.asOf ? formatDateTime(market.index.asOf) : '等待行情快照' }}</small>
        </div>
      </div>

      <MarketLineChart title="自选组合指数" :subtitle="indexSubtitle" :points="market.index?.points ?? []" :loading="market.loading" value-type="index" />

      <section class="watch-grid">
        <div class="watch-list card">
          <div class="section-head"><h2>自选列表</h2><span>点击查看单品走势</span></div>
          <button
            v-for="entry in market.watchlist"
            :key="entry.id"
            type="button"
            class="watch-row"
            :class="{ active: selectedWatch?.id === entry.id }"
            @click="selectWatch(entry)"
          >
            <span class="watch-name"><b>{{ entry.itemNameZh ?? entry.itemName }}</b><small>{{ entry.exterior ?? '无磨损' }}</small></span>
            <span class="watch-price num"><b>{{ entry.currentPrice == null ? '-' : formatMoney(entry.currentPrice) }}</b><small :class="(entry.changePercent24h ?? 0) >= 0 ? 'up' : 'down'">{{ entry.changePercent24h == null ? '暂无24h对比' : `${entry.changePercent24h >= 0 ? '+' : ''}${entry.changePercent24h.toFixed(2)}%` }}</small></span>
            <span class="remove" role="button" tabindex="0" aria-label="移出自选" @click.stop="deleteWatch(entry)">移除</span>
          </button>
          <div v-if="!market.loading && market.watchlist.length === 0" class="empty-state compact"><p>还没有自选饰品，从上方搜索并添加。</p></div>
        </div>
        <MarketLineChart
          :title="selectedWatch ? `${selectedWatch.itemNameZh ?? selectedWatch.itemName}${selectedWatch.exterior ? ` · ${selectedWatch.exterior}` : ''}` : '单品价格走势'"
          :subtitle="selectedWatch ? `悠悠有品市场价 · ${period}` : '先从左侧选择一个饰品'"
          :points="market.history?.points ?? []"
          :loading="market.loading"
          value-type="price"
        />
      </section>
    </template>

    <section v-else class="card market-placeholder">
      <div class="placeholder-mark">MARKET</div>
      <h2>全市场大盘数据源尚未接入</h2>
      <p>目前没有经过验证的全市场成分池和指数接口，因此不会用少量饰品拼接或生成虚假大盘走势。</p>
      <div class="placeholder-status"><span></span>持仓指数和自选指数可正常使用</div>
    </section>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.page-head h1 { margin-bottom: 3px; }
.page-head p, .add-watch p { margin: 0; color: var(--text-muted); font-size: 12px; }
.market-tabs { display: flex; align-items: center; gap: 3px; border-bottom: 1px solid var(--border); margin-bottom: 18px; }
.market-tabs > button, .periods button { border: 0; background: transparent; color: var(--text-secondary); cursor: pointer; font-size: 13px; padding: 9px 13px; border-bottom: 2px solid transparent; }
.market-tabs > button.active { color: var(--accent); border-bottom-color: var(--accent); font-weight: 600; }
.pending-dot { margin-left: 4px; padding: 2px 6px; border-radius: 999px; background: #eef0f3; color: var(--text-muted); font-size: 10px; }
.periods { margin-left: auto; display: flex; gap: 2px; }
.periods button { padding: 5px 9px; border: 1px solid transparent; border-radius: 999px; font-size: 11px; }
.periods button.active { color: var(--accent); background: var(--accent-soft); border-color: #c7d9fb; }
.metrics { display: grid; grid-template-columns: repeat(3, 1fr); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: 14px; overflow: hidden; }
.metric-block { padding: 14px 18px; display: flex; flex-direction: column; gap: 2px; border-right: 1px solid var(--border); }
.metric-block:last-child { border-right: 0; }
.metric-block span, .metric-block small { color: var(--text-muted); font-size: 11px; }
.metric-block strong { font-size: 22px; font-weight: 650; }
.list-section { margin-top: 20px; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 12px 14px; border-bottom: 1px solid var(--border); }
.list-section > .section-head { padding: 0 0 8px; border: 0; }
.section-head h2 { margin: 0; }
.section-head span { color: var(--text-muted); font-size: 11px; }
.num-head { text-align: right !important; }
.compact { padding: 22px; }
.add-watch { display: grid; grid-template-columns: minmax(190px, .8fr) minmax(260px, 1.4fr) 170px auto; gap: 10px; align-items: end; padding: 14px 16px; margin-bottom: 14px; }
.add-watch h2 { margin: 0 0 3px; }
.watch-grid { display: grid; grid-template-columns: minmax(300px, .8fr) minmax(0, 1.4fr); gap: 14px; margin-top: 14px; }
.watch-list { overflow: hidden; min-height: 350px; }
.watch-row { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr) 90px 44px; align-items: center; gap: 10px; padding: 10px 12px; border: 0; border-bottom: 1px solid var(--border); background: transparent; color: var(--text); text-align: left; cursor: pointer; }
.watch-row:hover { background: var(--surface-muted); }
.watch-row.active { background: var(--accent-soft); box-shadow: inset 3px 0 0 var(--accent); }
.watch-name, .watch-price { display: flex; flex-direction: column; min-width: 0; }
.watch-name b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.watch-name small, .watch-price small { color: var(--text-muted); font-size: 10px; }
.watch-price { text-align: right; }
.remove { color: var(--text-muted); font-size: 11px; text-align: right; }
.remove:hover { color: var(--danger); }
.market-placeholder { min-height: 380px; padding: 54px 24px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
.placeholder-mark { font-family: var(--font-mono); font-size: 11px; letter-spacing: .18em; color: var(--text-muted); border: 1px solid var(--border); padding: 4px 9px; }
.market-placeholder h2 { margin: 18px 0 8px; font-size: 18px; }
.market-placeholder p { max-width: 520px; margin: 0; color: var(--text-secondary); }
.placeholder-status { margin-top: 22px; font-size: 12px; color: var(--success); }
.placeholder-status span { display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: var(--success); margin-right: 6px; }
@media (max-width: 900px) {
  .add-watch { grid-template-columns: 1fr 1fr; }
  .watch-grid { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .page-head { flex-direction: column; }
  .page-head .btn { width: 100%; }
  .market-tabs { flex-wrap: wrap; }
  .periods { width: 100%; order: 2; overflow-x: auto; padding: 5px 0; }
  .metrics { grid-template-columns: 1fr; }
  .metric-block { border-right: 0; border-bottom: 1px solid var(--border); }
  .metric-block:last-child { border-bottom: 0; }
  .add-watch { grid-template-columns: 1fr; }
}
</style>
