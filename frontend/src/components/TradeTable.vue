<script setup lang="ts">
import type { Trade } from '../types'
import { formatDateTime, formatMoney, formatQty } from '../utils/format'

defineProps<{
  trades: Trade[]
  loading: boolean
  sortKey: 'tradedAt' | 'quantity' | 'unitPrice' | 'totalAmount'
  sortDir: 'asc' | 'desc'
}>()
const emit = defineEmits<{
  (e: 'edit', t: Trade): void
  (e: 'delete', t: Trade): void
  (e: 'sort', key: 'tradedAt' | 'quantity' | 'unitPrice' | 'totalAmount'): void
}>()

const platformLabel: Record<string, string> = { steam: 'Steam', uu: 'UU', buff: 'BUFF' }
const SORT_KEYS = ['tradedAt', 'quantity', 'unitPrice', 'totalAmount'] as const
</script>

<template>
  <div class="table-wrap">
    <table class="data">
      <thead>
        <tr>
          <th
            v-for="k in SORT_KEYS"
            :key="k"
            class="sortable num-head"
            :class="{ active: sortKey === k }"
            @click="emit('sort', k)"
          >
            {{ { tradedAt: '时间', quantity: '数量', unitPrice: '单价', totalAmount: '总额' }[k] }}
            <span class="sort-arrow" aria-hidden="true">
              {{ sortKey === k ? (sortDir === 'asc' ? '▲' : '▼') : '↕' }}
            </span>
          </th>
          <th>饰品</th><th>磨损</th><th class="num-head">磨损值</th><th>平台</th><th>方向</th>
          <th class="num-head">手续费</th><th>状态</th><th>备注</th><th></th>
        </tr>
      </thead>
      <tbody v-if="loading">
        <tr v-for="i in 6" :key="i">
          <td colspan="13"><div class="skeleton" style="height:14px;width:100%"></div></td>
        </tr>
      </tbody>
      <tbody v-else>
        <tr v-for="t in trades" :key="t.id">
          <td class="mono">{{ formatDateTime(t.tradedAt) }}</td>
          <td class="num">{{ formatQty(t.quantity) }}</td>
          <td class="num">{{ formatMoney(t.unitPrice) }}</td>
          <td class="num">{{ formatMoney(t.totalAmount) }}</td>
          <td>{{ t.itemNameZh ?? t.itemName }}</td>
          <td>{{ t.exterior ? t.exterior : '-' }}</td>
          <td class="num">{{ t.floatValue != null ? formatQty(t.floatValue) : '-' }}</td>
          <td><span class="badge badge-muted mono">{{ platformLabel[t.platform] ?? t.platform }}</span></td>
          <td>
            <span class="badge" :class="t.direction === 'BUY' ? 'badge-success' : 'badge-danger'">
              {{ t.direction === 'BUY' ? '买入' : '卖出' }}
            </span>
          </td>
          <td class="num">{{ formatMoney(t.fee) }}</td>
          <td>
            <span class="badge" :class="t.status === 'COMPLETED' ? 'badge-success' : 'badge-muted'">
              {{ t.status === 'COMPLETED' ? '已完成' : '进行中' }}
            </span>
          </td>
          <td :title="t.note ?? ''" class="note-cell">{{ t.note }}</td>
          <td class="row-actions">
            <button type="button" class="btn btn-ghost btn-sm" @click="emit('edit', t)">编辑</button>
            <button type="button" class="btn btn-ghost btn-sm danger-text" @click="emit('delete', t)">删除</button>
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
.note-cell { max-width: 150px; overflow: hidden; text-overflow: ellipsis; }
.row-actions { text-align: right; white-space: nowrap; }
.danger-text { color: var(--danger); }
.danger-text:hover { background: var(--danger-soft); }
</style>