<script setup lang="ts">
import type { Trade } from '../types'

defineProps<{ trades: Trade[] }>()
const emit = defineEmits<{ (e: 'edit', t: Trade): void; (e: 'delete', t: Trade): void }>()
</script>

<template>
  <table>
    <thead>
      <tr>
        <th>时间</th><th>饰品</th><th>平台</th><th>方向</th><th>数量</th>
        <th>单价</th><th>总额</th><th>手续费</th><th>状态</th><th>备注</th><th></th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="t in trades" :key="t.id">
        <td>{{ t.tradedAt.replace('T', ' ') }}</td>
        <td>{{ t.itemName }}</td>
        <td>{{ t.platform }}</td>
        <td :class="t.direction === 'BUY' ? 'buy' : 'sell'">
          {{ t.direction === 'BUY' ? '买入' : '卖出' }}
        </td>
        <td>{{ t.quantity }}</td>
        <td>{{ t.unitPrice }}</td>
        <td>{{ t.totalAmount }}</td>
        <td>{{ t.fee }}</td>
        <td>{{ t.status }}</td>
        <td>{{ t.note }}</td>
        <td>
          <button @click="emit('edit', t)">编辑</button>
          <button @click="emit('delete', t)">删除</button>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
table { width: 100%; border-collapse: collapse; background: #fff; }
th, td { border: 1px solid #e2e2e8; padding: 8px; text-align: left; font-size: 14px; }
th { background: #f0f1f4; }
.buy { color: #0a7d33; }
.sell { color: #c00; }
button { margin-right: 4px; }
</style>