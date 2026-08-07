<script setup lang="ts">
import type { Trade } from '../types'
import { formatDateTime, formatMoney, formatQty } from '../utils/format'

defineProps<{ trades: Trade[]; loading: boolean }>()
const emit = defineEmits<{ (e: 'edit', t: Trade): void; (e: 'delete', t: Trade): void }>()

const platformLabel: Record<string, string> = { steam: 'Steam', uu: 'UU', buff: 'BUFF' }
</script>

<template>
  <div class="table-wrap">
    <table class="data">
      <thead>
        <tr>
          <th>时间</th><th>饰品</th><th>磨损</th><th>磨损值</th><th>平台</th><th>方向</th><th>数量</th>
          <th class="num">单价</th><th class="num">总额</th><th class="num">手续费</th>
          <th>状态</th><th>备注</th><th></th>
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
          <td>{{ t.itemNameZh ?? t.itemName }}</td>
          <td>{{ t.exterior ?? '-' }}</td>
          <td class="num">{{ t.floatValue != null ? formatQty(t.floatValue) : '-' }}</td>
          <td><span class="badge badge-muted mono">{{ platformLabel[t.platform] ?? t.platform }}</span></td>
          <td>
            <span class="badge" :class="t.direction === 'BUY' ? 'badge-success' : 'badge-danger'">
              {{ t.direction === 'BUY' ? '买入' : '卖出' }}
            </span>
          </td>
          <td class="num">{{ formatQty(t.quantity) }}</td>
          <td class="num">{{ formatMoney(t.unitPrice) }}</td>
          <td class="num">{{ formatMoney(t.totalAmount) }}</td>
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
.note-cell { max-width: 160px; overflow: hidden; text-overflow: ellipsis; }
.row-actions { text-align: right; white-space: nowrap; }
.danger-text { color: var(--danger); }
.danger-text:hover { background: var(--danger-soft); }
</style>