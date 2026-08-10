<script setup lang="ts">
import type { Lot } from '../types'
import { formatDateTime, formatMoney, formatQty, formatSignedMoney } from '../utils/format'

type SortKey = 'buyTime' | 'buyPrice' | 'sellPrice' | 'profit' | 'quantity'

const props = defineProps<{
  lots: Lot[]
  loading: boolean
  sortKey: SortKey
  sortDir: 'asc' | 'desc'
  visibleColumns: string[]
  highlightId?: number | null
}>()
const emit = defineEmits<{
  (e: 'edit', lot: Lot): void
  (e: 'delete', lot: Lot): void
  (e: 'sell', lot: Lot): void
  (e: 'sort', key: SortKey): void
}>()

const platformLabel: Record<string, string> = { steam: 'Steam', uu: 'UU', buff: 'BUFF' }

function arrow(key: SortKey): string {
  if (props.sortKey !== key) return '↕'
  return props.sortDir === 'asc' ? '▲' : '▼'
}
</script>

<template>
  <div class="table-wrap">
    <table class="data">
      <thead>
        <tr>
          <th v-if="visibleColumns.includes('item')">饰品</th>
          <th v-if="visibleColumns.includes('exterior')">磨损</th>
          <th v-if="visibleColumns.includes('floatValue')" class="num-head">磨损值</th>
          <th v-if="visibleColumns.includes('quantity')" class="sortable num-head" :class="{ active: sortKey === 'quantity' }" @click="emit('sort', 'quantity')">数量 <span>{{ arrow('quantity') }}</span></th>
          <th v-if="visibleColumns.includes('buyPrice')" class="sortable num-head" :class="{ active: sortKey === 'buyPrice' }" @click="emit('sort', 'buyPrice')">买入价 <span>{{ arrow('buyPrice') }}</span></th>
          <th v-if="visibleColumns.includes('buyTime')" class="sortable num-head" :class="{ active: sortKey === 'buyTime' }" @click="emit('sort', 'buyTime')">买入时间 <span>{{ arrow('buyTime') }}</span></th>
          <th v-if="visibleColumns.includes('buyPlatform')">买入平台</th>
          <th v-if="visibleColumns.includes('sellPrice')" class="sortable num-head" :class="{ active: sortKey === 'sellPrice' }" @click="emit('sort', 'sellPrice')">出售价 <span>{{ arrow('sellPrice') }}</span></th>
          <th v-if="visibleColumns.includes('actualIncome')" class="num-head">实际收入</th>
          <th v-if="visibleColumns.includes('fee')" class="num-head">手续费</th>
          <th v-if="visibleColumns.includes('sellTime')" class="num-head">出售时间</th>
          <th v-if="visibleColumns.includes('sellPlatform')">出售平台</th>
          <th v-if="visibleColumns.includes('profit')" class="sortable num-head" :class="{ active: sortKey === 'profit' }" @click="emit('sort', 'profit')">盈亏 <span>{{ arrow('profit') }}</span></th>
          <th v-if="visibleColumns.includes('status')">状态</th>
          <th v-if="visibleColumns.includes('note')">备注</th>
          <th></th>
        </tr>
      </thead>
      <tbody v-if="loading">
        <tr v-for="i in 6" :key="i">
          <td :colspan="visibleColumns.length + 1"><div class="skeleton" style="height:14px;width:100%"></div></td>
        </tr>
      </tbody>
      <tbody v-else>
        <tr v-for="lot in lots" :key="lot.id" :data-lot="lot.id" :class="{ sold: lot.status === 'SOLD', highlight: lot.id === props.highlightId }">
          <td v-if="visibleColumns.includes('item')">{{ lot.itemNameZh ?? lot.itemName }}</td>
          <td v-if="visibleColumns.includes('exterior')">{{ lot.exterior ?? '-' }}</td>
          <td v-if="visibleColumns.includes('floatValue')" class="num">{{ lot.floatValue != null ? formatQty(lot.floatValue) : '-' }}</td>
          <td v-if="visibleColumns.includes('quantity')" class="num">{{ formatQty(lot.quantity) }}</td>
          <td v-if="visibleColumns.includes('buyPrice')" class="num">{{ formatMoney(lot.buyPrice) }}<span v-if="lot.status === 'HOLDING' && lot.buyPrice === 0" class="badge badge-danger pending-badge">待补填</span></td>
          <td v-if="visibleColumns.includes('buyTime')" class="mono">{{ formatDateTime(lot.buyTime) }}</td>
          <td v-if="visibleColumns.includes('buyPlatform')"><span class="badge badge-muted mono">{{ platformLabel[lot.buyPlatform] ?? lot.buyPlatform }}</span></td>
          <td v-if="visibleColumns.includes('sellPrice')" class="num">{{ lot.sellPrice != null ? formatMoney(lot.sellPrice) : '-' }}</td>
          <td v-if="visibleColumns.includes('actualIncome')" class="num">{{ lot.actualIncome != null ? formatMoney(lot.actualIncome) : '-' }}</td>
          <td v-if="visibleColumns.includes('fee')" class="num">{{ formatMoney(lot.fee) }}</td>
          <td v-if="visibleColumns.includes('sellTime')" class="mono">{{ lot.sellTime ? formatDateTime(lot.sellTime) : '-' }}</td>
          <td v-if="visibleColumns.includes('sellPlatform')">{{ lot.sellPlatform ? platformLabel[lot.sellPlatform] ?? lot.sellPlatform : '-' }}</td>
          <td v-if="visibleColumns.includes('profit')" class="num" :class="lot.profit != null ? (lot.profit >= 0 ? 'up' : 'down') : ''">
            {{ lot.profit != null ? formatSignedMoney(lot.profit) : '-' }}
          </td>
          <td v-if="visibleColumns.includes('status')">
            <span class="badge" :class="lot.status === 'HOLDING' ? 'badge-accent' : 'badge-muted'">
              {{ lot.status === 'HOLDING' ? '持仓中' : '已卖出' }}
            </span>
          </td>
          <td v-if="visibleColumns.includes('note')" :title="lot.note ?? ''" class="note-cell">{{ lot.note }}</td>
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
th.sortable span { font-size: 10px; margin-left: 2px; opacity: .7; }
.num-head { text-align: right; }
td.num { text-align: right; font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
tbody tr.sold td { color: var(--text-secondary); }
.note-cell { max-width: 140px; overflow: hidden; text-overflow: ellipsis; }
.row-actions { text-align: right; white-space: nowrap; }
.sell-btn { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); }
.sell-btn:hover { background: var(--accent); color: #fff; }
.danger-text { color: var(--danger); }
tr.highlight { background: var(--accent-soft) !important; box-shadow: inset 3px 0 0 var(--accent); }
.pending-badge { margin-left: 6px; font-size: 11px; padding: 0 6px; }
.danger-text:hover { background: var(--danger-soft); }
</style>
