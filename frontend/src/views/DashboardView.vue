<script setup lang="ts">
import { onMounted } from 'vue'
import PnlChart from '../components/PnlChart.vue'
import { useTradesStore } from '../stores/trades'
import { formatMoney, formatQty, formatSignedMoney } from '../utils/format'

const store = useTradesStore()

async function loadAll() {
  await Promise.all([store.loadPortfolio(), store.loadPnl('month')])
}

onMounted(loadAll)
</script>

<template>
  <div>
    <h1>仪表盘</h1>

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
        <span class="metric-sub">已平仓部分扣除手续费后的净盈亏</span>
      </div>
      <div class="card metric">
        <span class="metric-label">当前持仓</span>
        <div v-if="store.loadingPortfolio" class="skeleton" style="height:28px;width:60px;margin-top:8px"></div>
        <div v-else class="metric-value num">{{ store.holdings.length }}</div>
        <span class="metric-sub">当前持有数量的饰品品种数</span>
      </div>
    </div>

    <PnlChart :rows="store.pnlRows" :loading="store.loadingPnl" />

    <h2>当前持仓</h2>
    <div class="table-wrap">
      <table class="data">
        <thead>
          <tr>
            <th>饰品</th><th class="num">数量</th><th class="num">平均成本</th>
            <th class="num">已实现盈亏</th><th class="num">浮动盈亏</th>
          </tr>
        </thead>
        <tbody v-if="store.loadingPortfolio">
          <tr v-for="i in 3" :key="i">
            <td colspan="5"><div class="skeleton" style="height:14px;width:100%"></div></td>
          </tr>
        </tbody>
        <tbody v-else>
          <tr v-for="h in store.holdings" :key="h.itemName">
            <td>{{ h.itemName }}</td>
            <td class="num">{{ formatQty(h.quantity) }}</td>
            <td class="num">{{ formatMoney(h.avgCost) }}</td>
            <td class="num" :class="h.realizedPnl >= 0 ? 'up' : 'down'">{{ formatSignedMoney(h.realizedPnl) }}</td>
            <td class="num">{{ h.unrealizedPnl == null ? '-' : formatMoney(h.unrealizedPnl) }}</td>
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
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 24px; }
.metric { padding: 16px 18px; display: flex; flex-direction: column; gap: 4px; }
.metric-label { font-size: 12px; font-weight: 550; color: var(--text-secondary); }
.metric-value { font-size: 26px; font-weight: 650; letter-spacing: -.01em; }
.metric-sub { font-size: 12px; color: var(--text-muted); }
@media (max-width: 760px) { .cards { grid-template-columns: 1fr; } }
</style>