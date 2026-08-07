<script setup lang="ts">
import { onMounted } from 'vue'
import PnlChart from '../components/PnlChart.vue'
import { useTradesStore } from '../stores/trades'
import { formatMoney } from '../utils/format'

const store = useTradesStore()

onMounted(async () => {
  await Promise.all([store.loadPortfolio(), store.loadPnl('month')])
})
</script>

<template>
  <div>
    <h1>仪表盘</h1>
    <div class="cards">
      <div class="card">
        <div class="label">持仓总成本</div>
        <div class="value">{{ formatMoney(store.totalCost) }}</div>
      </div>
      <div class="card">
        <div class="label">已实现盈亏</div>
        <div class="value" :class="store.totalRealizedPnl >= 0 ? 'up' : 'down'">
          {{ formatMoney(store.totalRealizedPnl) }}
        </div>
      </div>
      <div class="card">
        <div class="label">持仓数</div>
        <div class="value">{{ store.holdings.length }}</div>
      </div>
    </div>

    <PnlChart :rows="store.pnlRows" />

    <h2>当前持仓</h2>
    <table>
      <thead>
        <tr><th>饰品</th><th>数量</th><th>平均成本</th><th>已实现盈亏</th><th>浮动盈亏</th></tr>
      </thead>
      <tbody>
        <tr v-for="h in store.holdings" :key="h.itemName">
          <td>{{ h.itemName }}</td>
          <td>{{ h.quantity }}</td>
          <td>{{ formatMoney(h.avgCost) }}</td>
          <td :class="h.realizedPnl >= 0 ? 'up' : 'down'">{{ formatMoney(h.realizedPnl) }}</td>
          <td>{{ h.unrealizedPnl == null ? '-' : formatMoney(h.unrealizedPnl) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.cards { display: flex; gap: 16px; margin-bottom: 24px; }
.card { background: #fff; border-radius: 8px; padding: 16px 24px; flex: 1; box-shadow: 0 1px 3px rgba(0,0,0,.08); }
.label { font-size: 13px; color: #666; }
.value { font-size: 24px; font-weight: 600; margin-top: 6px; }
.up { color: #0a7d33; }
.down { color: #c00; }
table { width: 100%; border-collapse: collapse; background: #fff; }
th, td { border: 1px solid #e2e2e8; padding: 8px; text-align: left; font-size: 14px; }
th { background: #f0f1f4; }
</style>