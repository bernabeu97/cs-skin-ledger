<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getItem } from '../api/client'
import ItemSelect from './ItemSelect.vue'
import type { Item, Trade, TradeCreateRequest } from '../types'
import { formatMoney } from '../utils/format'

const DEFAULT_WEARS = ['崭新出厂', '略有磨损', '久经沙场', '破损不堪', '战痕累累']

const props = defineProps<{ editing: Trade | null; saving: boolean }>()
const emit = defineEmits<{
  (e: 'close'): void
  (e: 'saved', payload: TradeCreateRequest): void
}>()

const selectedItem = ref<Item | null>(null)
const manualName = ref(props.editing?.itemName ?? '')
const form = reactive({
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
  note: props.editing?.note ?? '',
  exterior: props.editing?.exterior ?? '',
  floatValue: props.editing?.floatValue != null ? String(props.editing.floatValue) : ''
})

const errors = reactive<{ item?: string; quantity?: string; unitPrice?: string; tradedAt?: string }>({})
const firstField = ref<HTMLInputElement | null>(null)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

async function loadEditingItem() {
  if (props.editing?.itemId) {
    try {
      selectedItem.value = await getItem(props.editing.itemId)
      manualName.value = selectedItem.value.marketHashName
    } catch {
      selectedItem.value = null
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  loadEditingItem()
  firstField.value?.focus()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

const totalPreview = computed(() => {
  const qty = Number(form.quantity)
  const price = Number(form.unitPrice)
  const fee = Number(form.fee) || 0
  if (!qty || qty <= 0 || Number.isNaN(price) || price < 0) return null
  return qty * price + fee
})
const wearOptions = ref<string[]>(DEFAULT_WEARS)
watch(selectedItem, (item) => {
  if (item?.minFloat != null) {
    wearOptions.value = item.wears && item.wears.length ? item.wears : DEFAULT_WEARS
  } else {
    form.exterior = ''
    form.floatValue = ''
  }
})

function validate(): boolean {
  const itemOk = selectedItem.value || manualName.value.trim()
  errors.item = itemOk ? undefined : '请选择或输入饰品'
  const qty = Number(form.quantity)
  errors.quantity = !form.quantity || Number.isNaN(qty) || qty <= 0 ? '数量必须大于 0' : undefined
  const price = Number(form.unitPrice)
  errors.unitPrice = !form.unitPrice || Number.isNaN(price) || price < 0 ? '单价不能为负' : undefined
  errors.tradedAt = form.tradedAt ? undefined : '请选择成交时间'
  return !errors.item && !errors.quantity && !errors.unitPrice && !errors.tradedAt
}

function submit() {
  if (props.saving || !validate()) return
  const tradedAt = form.tradedAt.length === 16 ? form.tradedAt + ':00' : form.tradedAt
  const floatValue = form.floatValue ? Number(form.floatValue) : undefined
  if (floatValue !== undefined && (floatValue < 0 || floatValue > 1)) {
    errors.quantity = undefined
    errors.tradedAt = undefined
    errors.item = undefined
    errors.unitPrice = undefined
    window.alert('磨损值必须在 0-1 之间')
    return
  }
  emit('saved', {
    itemId: selectedItem.value?.id,
    itemName: selectedItem.value?.marketHashName ?? manualName.value.trim(),
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
    note: form.note || undefined,
    exterior: form.exterior || undefined,
    floatValue
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
            <span>饰品（数据字典） <i class="req">*</i></span>
            <ItemSelect v-model="selectedItem" placeholder="输入关键词搜索饰品（支持中文）" />
            <p class="field-error" v-if="errors.item">{{ errors.item }}</p>
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

          <template v-if="selectedItem?.minFloat != null">
            <div class="field">
              <span>磨损等级</span>
              <select v-model="form.exterior" class="select">
                <option value="">不指定</option>
                <option v-for="w in wearOptions" :key="w" :value="w">{{ w }}</option>
              </select>
            </div>
            <div class="field">
              <span>磨损值（0-1）</span>
              <input v-model="form.floatValue" class="input num" type="number" step="0.0001" min="0" max="1" placeholder="如 0.1234" />
              <p class="field-hint" v-if="selectedItem?.minFloat != null">磨损区间 {{ selectedItem.minFloat }} - {{ selectedItem.maxFloat }}</p>
            </div>
          </template>

          <div class="field wide">
            <span>备注</span>
            <input v-model="form.note" class="input" />
          </div>
        </div>

        <div class="form-actions">
          <div class="total-preview" v-if="totalPreview !== null">
            合计 ≈ <b class="num">{{ formatMoney(totalPreview) }} {{ form.currency }}</b>
          </div>
          <div class="actions-right">
            <button type="button" class="btn" :disabled="props.saving" @click="emit('close')">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="props.saving">
              {{ props.saving ? '保存中…' : '保存' }}
            </button>
          </div>
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
.field-hint { font-size: 11px; color: var(--text-muted); margin: 0; }
.total-preview { font-size: 13px; color: var(--text-secondary); align-self: center; }
.actions-right { display: flex; gap: 8px; }
.form-actions {
  display: flex; gap: 8px; justify-content: space-between; align-items: center;
  padding: 14px 20px; border-top: 1px solid var(--border); background: var(--surface-muted);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
@media (max-width: 640px) {
  .grid { grid-template-columns: 1fr; }
}
</style>