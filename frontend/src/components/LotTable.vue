<script setup lang="ts">
import type { Lot } from '../types'
import { formatDateTime, formatMoney, formatQty, formatSignedMoney } from '../utils/format'

defineProps<{
  lots: Lot[]
  loading: boolean
  sortKey: 'buyTime' | 'buyPrice' | 'sellPrice' | 'profit' | 'quantity'
  sortDir: 'asc' | 'desc'
}>()
const emit = defineEmits<{
  (e: 'edit', lot: Lot): void
  (e: 'delete', lot: Lot): void
  (e: 'sell', lot: Lot): void
  (e: 'sort', key: 'buyTime' | 'buyPrice' | 'sellPrice' | 'profit' | 'quantity'): void
}>()

const platformLabel: Record<string, string> = { steam: 'Steam', uu: 'UU', buff: 'BUFF' }
const SORT_KEYS = ['buyTime', 'buyPrice', 'sellPrice', 'profit', 'quantity'] as const
const HEAD_LABEL: Record<string, string> = {
  buyTime: '买入时间', buyPrice: '买入价', sellPrice: '出售价', profit: '盈亏', quantity: '数量'
}
</script>

<template>
  <div class="table-wrap">
    <table class="data">
      <thead>
        <tr>
          <th>饰品</th><th>磨损</th><th class="num-head">磨损值</th>
          <th
            v-for="k in SORT_KEYS"
            :key="k"
            class="sortable num-head"
            :class="{ active: sortKey === k }"
            @click="emit('sort', k)"
          >
            {{ HEAD_LABEL[k] }}
            <span class="sort-arrow" aria-hidden="true">
              {{ sortKey === k ? (sortDir === 'asc' ? '▲' : '▼') : '↕' }}
            </span>
          </th>
          <th class="num-head">实际收入</th><th class="num-head">手续费</th>
          <th class="num-head">出售时间</th><th>买入平台</th><th>出售平台</th>
          <th>状态</th><th>备注</th><th></th>
        </tr>
      </thead>
      <tbody v-if="loading">
        <tr v-for="i in 6" :key="i">
          <td colspan="14"><div class="skeleton" style="height:14px;width:100%"></div></td>
        </tr>
      </tbody>
      <tbody v-else>
        <tr v-for="lot in lots" :key="lot.id" :class="{ sold: lot.status === 'SOLD' }">
          <td>{{ lot.itemNameZh ?? lot.itemName }}</td>
          <td>{{ lot.exterior ?? '-' }}</td>
          <td class="num">{{ lot.floatValue != null ? formatQty(lot.floatValue) : '-' }}</td>
          <td class="num">{{ formatQty(lot.quantity) }}</td>
          <td class="num">{{ formatMoney(lot.buyPrice) }}</td>
          <td class="mono">{{ formatDateTime(lot.buyTime) }}</td>
          <td class="num">{{ lot.sellPrice != null ? formatMoney(lot.sellPrice) : '-' }}</td>
          <td class="num">{{ lot.actualIncome != null ? formatMoney(lot.actualIncome) : '-' }}</td>
          <td class="num">{{ formatMoney(lot.fee) }}</td>
          <td class="mono">{{ lot.sellTime ? formatDateTime(lot.sellTime) : '-' }}</td>
          <td><span class="badge badge-muted mono">{{ platformLabel[lot.buyPlatform] ?? lot.buyPlatform }}</span></td>
          <td>{{ lot.sellPlatform ? platformLabel[lot.sellPlatform] ?? lot.sellPlatform : '-' }}</td>
          <td>
            <span class="badge" :class="lot.status === 'HOLDING' ? 'badge-accent' : 'badge-muted'">
              {{ lot.status === 'HOLDING' ? '持仓中' : '已卖出' }}
            </span>
          </td>
          <td :title="lot.note ?? ''" class="note-cell">{{ lot.note }}</td>
          <td class="row-actions">
            <button v-if="lot.status === 'HOLDING'" type="button" class="btn btn-sm sell-btn" @click="emit('sell', lot)">更新卖出</button>
            <button type="button" class="btn btn-ghost btn-sm" @click="emit('edit', lot)">编辑</button>
            <button type="button" class="btn btn-ghost btn-sm danger-text" @click="emit('delete', lot)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
th.sortable { cursor: pointer; user-select: none; }
th.sortable:hover { color: var(--text); }
th.sortable.active { color: var(--accent); }
.sort-arrow { font-size: 10px; margin-left: 2px; opacity: .7; }
.num-head { text-align: right; }
td.num { text-align: right; font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
tbody tr.sold td { color: var(--text-secondary); }
.note-cell { max-width: 140px; overflow: hidden; text-overflow: ellipsis; }
.row-actions { text-align: right; white-space: nowrap; }
.sell-btn { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); }
.sell-btn:hover { background: var(--accent); color: #fff; }
.danger-text { color: var(--danger); }
.danger-text:hover { background: var(--danger-soft); }
</style>