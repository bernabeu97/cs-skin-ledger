<script setup lang="ts">
import { reactive } from 'vue'
import type { Trade, TradeCreateRequest } from '../types'

const props = defineProps<{ editing: Trade | null }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved', payload: TradeCreateRequest): void
}>()

const form = reactive({
  itemName: props.editing?.itemName ?? '',
  platform: props.editing?.platform ?? 'steam',
  direction: props.editing?.direction ?? 'BUY',
  quantity: props.editing ? String(props.editing.quantity) : '1',
  unitPrice: props.editing ? String(props.editing.unitPrice) : '',
  fee: props.editing ? String(props.editing.fee) : '0',
  feeRate: props.editing?.feeRate != null ? String(props.editing.feeRate) : '',
  currency: props.editing?.currency ?? 'CNY',
  tradedAt: props.editing
    ? props.editing.tradedAt.slice(0, 16)
    : new Date().toISOString().slice(0, 16),
  externalTradeId: props.editing?.externalTradeId ?? '',
  status: props.editing?.status ?? 'COMPLETED',
  note: props.editing?.note ?? ''
})

function submit() {
  const tradedAt = form.tradedAt.length === 16 ? form.tradedAt + ':00' : form.tradedAt
  emit('saved', {
    itemName: form.itemName.trim(),
    platform: form.platform,
    direction: form.direction as 'BUY' | 'SELL',
    quantity: Number(form.quantity),
    unitPrice: Number(form.unitPrice),
    fee: Number(form.fee),
    feeRate: form.feeRate ? Number(form.feeRate) : undefined,
    currency: form.currency,
    tradedAt,
    externalTradeId: form.externalTradeId || undefined,
    status: form.status as 'COMPLETED' | 'PENDING',
    note: form.note || undefined
  })
}
</script>

<template>
  <div class="mask">
    <div class="panel">
      <h2>{{ props.editing ? '编辑交易' : '新增交易' }}</h2>
      <div class="grid">
        <label>饰品名称<input v-model="form.itemName" /></label>
        <label>平台
          <select v-model="form.platform">
            <option value="steam">Steam</option>
            <option value="uu">UU</option>
            <option value="buff">BUFF</option>
          </select>
        </label>
        <label>方向
          <select v-model="form.direction">
            <option value="BUY">买入</option>
            <option value="SELL">卖出</option>
          </select>
        </label>
        <label>数量<input v-model="form.quantity" type="number" step="0.0001" min="0.0001" /></label>
        <label>单价<input v-model="form.unitPrice" type="number" step="0.0001" min="0" /></label>
        <label>手续费<input v-model="form.fee" type="number" step="0.0001" min="0" /></label>
        <label>费率 %<input v-model="form.feeRate" type="number" step="0.000001" min="0" /></label>
        <label>币种<input v-model="form.currency" /></label>
        <label>成交时间<input v-model="form.tradedAt" type="datetime-local" /></label>
        <label>平台单号<input v-model="form.externalTradeId" /></label>
        <label>状态
          <select v-model="form.status">
            <option value="COMPLETED">已完成</option>
            <option value="PENDING">进行中</option>
          </select>
        </label>
        <label class="wide">备注<input v-model="form.note" /></label>
      </div>
      <div class="actions">
        <button class="primary" @click="submit">保存</button>
        <button @click="emit('close')">取消</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mask { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; }
.panel { background: #fff; padding: 20px 24px; border-radius: 8px; width: 560px; max-height: 90vh; overflow: auto; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; }
.grid .wide { grid-column: 1 / -1; }
input, select { padding: 6px 8px; }
.actions { margin-top: 16px; display: flex; gap: 8px; justify-content: flex-end; }
.primary { background: #2563eb; color: #fff; border: none; padding: 8px 16px; border-radius: 4px; }
</style>