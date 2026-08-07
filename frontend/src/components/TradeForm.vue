<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { Trade, TradeCreateRequest } from '../types'

const props = defineProps<{ editing: Trade | null; saving: boolean }>()
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

const errors = reactive<{ itemName?: string; quantity?: string; unitPrice?: string; tradedAt?: string }>({})
const firstField = ref<HTMLInputElement | null>(null)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  firstField.value?.focus()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function validate(): boolean {
  errors.itemName = form.itemName.trim() ? undefined : '请输入饰品名称'
  const qty = Number(form.quantity)
  errors.quantity = !form.quantity || Number.isNaN(qty) || qty <= 0 ? '数量必须大于 0' : undefined
  const price = Number(form.unitPrice)
  errors.unitPrice = !form.unitPrice || Number.isNaN(price) || price < 0 ? '单价不能为负' : undefined
  errors.tradedAt = form.tradedAt ? undefined : '请选择成交时间'
  return !errors.itemName && !errors.quantity && !errors.unitPrice && !errors.tradedAt
}

function submit() {
  if (props.saving || !validate()) return
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
  <div class="dialog-mask" @click.self="emit('close')">
    <div class="dialog-panel form-panel" role="dialog" aria-modal="true" aria-label="交易表单">
      <form novalidate @submit.prevent="submit">
        <div class="form-header">
          <h2>{{ props.editing ? '编辑交易' : '新增交易' }}</h2>
          <button type="button" class="close-btn" aria-label="关闭" @click="emit('close')">×</button>
        </div>

        <div class="grid">
          <div class="field wide">
            <span>饰品名称 <i class="req">*</i></span>
            <input ref="firstField" v-model="form.itemName" class="input" placeholder="如 AK-47 | Redline (Field-Tested)" @input="errors.itemName = undefined" />
            <p v-if="errors.itemName" class="field-error">{{ errors.itemName }}</p>
          </div>

          <div class="field">
            <span>平台</span>
            <select v-model="form.platform" class="select">
              <option value="steam">Steam</option>
              <option value="uu">UU</option>
              <option value="buff">BUFF</option>
            </select>
          </div>

          <div class="field">
            <span>方向</span>
            <select v-model="form.direction" class="select">
              <option value="BUY">买入</option>
              <option value="SELL">卖出</option>
            </select>
          </div>

          <div class="field">
            <span>数量 <i class="req">*</i></span>
            <input v-model="form.quantity" class="input" type="number" step="0.0001" min="0.0001" @input="errors.quantity = undefined" />
            <p v-if="errors.quantity" class="field-error">{{ errors.quantity }}</p>
          </div>

          <div class="field">
            <span>单价 <i class="req">*</i></span>
            <input v-model="form.unitPrice" class="input" type="number" step="0.0001" min="0" @input="errors.unitPrice = undefined" />
            <p v-if="errors.unitPrice" class="field-error">{{ errors.unitPrice }}</p>
          </div>

          <div class="field">
            <span>手续费</span>
            <input v-model="form.fee" class="input" type="number" step="0.0001" min="0" />
          </div>

          <div class="field">
            <span>费率 %</span>
            <input v-model="form.feeRate" class="input" type="number" step="0.000001" min="0" />
          </div>

          <div class="field">
            <span>币种</span>
            <input v-model="form.currency" class="input" />
          </div>

          <div class="field">
            <span>成交时间 <i class="req">*</i></span>
            <input v-model="form.tradedAt" class="input" type="datetime-local" @input="errors.tradedAt = undefined" />
            <p v-if="errors.tradedAt" class="field-error">{{ errors.tradedAt }}</p>
          </div>

          <div class="field">
            <span>平台单号</span>
            <input v-model="form.externalTradeId" class="input mono" />
          </div>

          <div class="field">
            <span>状态</span>
            <select v-model="form.status" class="select">
              <option value="COMPLETED">已完成</option>
              <option value="PENDING">进行中</option>
            </select>
          </div>

          <div class="field wide">
            <span>备注</span>
            <input v-model="form.note" class="input" />
          </div>
        </div>

        <div class="form-actions">
          <button type="button" class="btn" :disabled="props.saving" @click="emit('close')">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="props.saving">
            {{ props.saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.form-panel { padding: 0; }
.form-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px 12px; border-bottom: 1px solid var(--border);
}
.form-header h2 { margin: 0; }
.close-btn {
  border: none; background: none; font-size: 20px; line-height: 1; color: var(--text-muted);
  cursor: pointer; padding: 4px 8px; border-radius: var(--radius-sm);
}
.close-btn:hover { color: var(--text); background: rgba(16,24,40,.06); }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; padding: 16px 20px; }
.field .req { font-style: normal; }
.form-actions {
  display: flex; gap: 8px; justify-content: flex-end;
  padding: 14px 20px; border-top: 1px solid var(--border); background: var(--surface-muted);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
@media (max-width: 640px) {
  .grid { grid-template-columns: 1fr; }
}
</style>