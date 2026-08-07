<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { Lot, LotSellRequest } from '../types'
import { formatMoney } from '../utils/format'

const props = defineProps<{ lot: Lot; saving: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved', payload: LotSellRequest): void }>()

const form = reactive({
  sellPrice: props.lot.sellPrice != null ? String(props.lot.sellPrice) : '',
  sellTime: props.lot.sellTime ? props.lot.sellTime.slice(0, 16) : new Date().toISOString().slice(0, 16),
  sellPlatform: props.lot.sellPlatform ?? props.lot.buyPlatform,
  fee: props.lot.fee != null ? String(props.lot.fee) : '0'
})
const errors = reactive<{ sellPrice?: string; fee?: string }>({})
const firstField = ref<HTMLInputElement | null>(null)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => {
  window.addEventListener('keydown', onKey)
  firstField.value?.focus()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function submit() {
  const price = Number(form.sellPrice)
  errors.sellPrice = !form.sellPrice || Number.isNaN(price) || price < 0 ? '请输入有效的出售价' : undefined
  const fee = Number(form.fee) || 0
  errors.fee = Number.isNaN(fee) || fee < 0 ? '手续费不能为负' : undefined
  if (errors.sellPrice || errors.fee) return
  const sellTime = form.sellTime.length === 16 ? form.sellTime + ':00' : form.sellTime
  emit('saved', {
    sellPrice: price,
    sellTime,
    sellPlatform: form.sellPlatform || undefined,
    fee
  })
}
</script>

<template>
  <div class="dialog-mask" @click.self="emit('close')">
    <div class="dialog-panel form-panel" role="dialog" aria-modal="true" aria-label="更新卖出">
      <form novalidate @submit.prevent="submit">
        <div class="form-header">
          <h2>更新卖出</h2>
          <button type="button" class="close-btn" aria-label="关闭" @click="emit('close')">×</button>
        </div>
        <div class="buy-info">
          买入：{{ props.lot.itemNameZh ?? props.lot.itemName }} · {{ formatMoney(props.lot.buyPrice) }}
          <span v-if="props.lot.exterior"> · {{ props.lot.exterior }}</span>
        </div>
        <div class="grid">
          <div class="field">
            <span>出售价 <i class="req">*</i></span>
            <input ref="firstField" v-model="form.sellPrice" class="input num" type="number" step="0.01" min="0" @input="errors.sellPrice = undefined" />
            <p v-if="errors.sellPrice" class="field-error">{{ errors.sellPrice }}</p>
          </div>
          <div class="field">
            <span>手续费</span>
            <input v-model="form.fee" class="input num" type="number" step="0.01" min="0" />
            <p v-if="errors.fee" class="field-error">{{ errors.fee }}</p>
          </div>
          <div class="field">
            <span>出售时间</span>
            <input v-model="form.sellTime" class="input" type="datetime-local" />
          </div>
          <div class="field">
            <span>出售平台</span>
            <select v-model="form.sellPlatform" class="select">
              <option value="steam">Steam</option>
              <option value="uu">UU</option>
              <option value="buff">BUFF</option>
            </select>
          </div>
        </div>
        <div class="form-actions">
          <div class="total-preview">
            预计实际收入 ≈ <b class="num">{{ Number(form.sellPrice || 0) - (Number(form.fee) || 0) }}</b>
          </div>
          <div class="actions-right">
            <button type="button" class="btn" :disabled="props.saving" @click="emit('close')">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="props.saving">
              {{ props.saving ? '保存中…' : '确认卖出' }}
            </button>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.form-panel { padding: 0; }
.form-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px 12px; border-bottom: 1px solid var(--border); }
.form-header h2 { margin: 0; }
.close-btn { border: none; background: none; font-size: 20px; line-height: 1; color: var(--text-muted); cursor: pointer; padding: 4px 8px; border-radius: var(--radius-sm); }
.close-btn:hover { color: var(--text); background: rgba(16,24,40,.06); }
.buy-info { padding: 12px 20px 0; font-size: 12px; color: var(--text-muted); }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; padding: 14px 20px; }
.field .req { font-style: normal; }
.form-actions { display: flex; gap: 8px; justify-content: space-between; align-items: center; padding: 14px 20px; border-top: 1px solid var(--border); background: var(--surface-muted); border-radius: 0 0 var(--radius-lg) var(--radius-lg); }
.total-preview { font-size: 13px; color: var(--text-secondary); }
.actions-right { display: flex; gap: 8px; }
@media (max-width: 640px) { .grid { grid-template-columns: 1fr; } }
</style>