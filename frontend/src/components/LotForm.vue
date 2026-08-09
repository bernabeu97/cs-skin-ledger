<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getItem } from '../api/client'
import ItemSelect from './ItemSelect.vue'
import type { Item, Lot, LotCreateRequest } from '../types'
import { formatMoney } from '../utils/format'

const DEFAULT_WEARS = ['崭新出厂', '略有磨损', '久经沙场', '破损不堪', '战痕累累']

const props = defineProps<{ editing: Lot | null; saving: boolean; attention?: boolean }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved', payload: LotCreateRequest): void }>()

const selectedItem = ref<Item | null>(null)
const manualName = ref(props.editing?.itemName ?? '')
const form = reactive({
  quantity: props.editing ? String(props.editing.quantity) : '1',
  exterior: props.editing?.exterior ?? '',
  floatValue: props.editing?.floatValue != null ? String(props.editing.floatValue) : '',
  buyPrice: props.editing ? String(props.editing.buyPrice) : '',
  buyTime: props.editing
    ? props.editing.buyTime.slice(0, 16)
    : new Date().toISOString().slice(0, 16),
  buyPlatform: props.editing?.buyPlatform ?? 'uu',
  note: props.editing?.note ?? '',
  sellPrice: props.editing?.sellPrice != null ? String(props.editing.sellPrice) : '',
  sellTime: props.editing?.sellTime ? props.editing.sellTime.slice(0, 16) : '',
  sellPlatform: props.editing?.sellPlatform ?? props.editing?.buyPlatform ?? 'uu',
  fee: props.editing?.fee != null ? String(props.editing.fee) : '0'
})
const errors = reactive<{ item?: string; buyPrice?: string; sellPrice?: string; fee?: string }>({})
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

const wearOptions = ref<string[]>(DEFAULT_WEARS)
watch(selectedItem, (item) => {
  if (item?.minFloat != null) {
    wearOptions.value = item.wears && item.wears.length ? item.wears : DEFAULT_WEARS
  } else {
    form.exterior = ''
    form.floatValue = ''
  }
})

const totalPreview = computed(() => {
  const price = Number(form.buyPrice)
  const qty = Number(form.quantity) || 1
  if (Number.isNaN(price) || price < 0) return null
  return price * qty
})

function validate(): boolean {
  errors.item = selectedItem.value || manualName.value.trim() ? undefined : '请选择或输入饰品'
  const price = Number(form.buyPrice)
  errors.buyPrice = !form.buyPrice || Number.isNaN(price) || price < 0 ? '请输入有效的买入价' : undefined
  if (props.editing && form.sellPrice !== '') {
    const sp = Number(form.sellPrice)
    errors.sellPrice = Number.isNaN(sp) || sp < 0 ? '出售价不能为负' : undefined
  } else {
    errors.sellPrice = undefined
  }
  if (props.editing && form.fee !== '') {
    const fee = Number(form.fee)
    errors.fee = Number.isNaN(fee) || fee < 0 ? '手续费不能为负' : undefined
  } else {
    errors.fee = undefined
  }
  return !errors.item && !errors.buyPrice && !errors.sellPrice && !errors.fee
}

function submit() {
  if (props.saving || !validate()) return
  const buyTime = form.buyTime.length === 16 ? form.buyTime + ':00' : form.buyTime
  const floatValue = form.floatValue ? Number(form.floatValue) : undefined
  if (floatValue !== undefined && (floatValue < 0 || floatValue > 1)) {
    window.alert('磨损值必须在 0-1 之间')
    return
  }
  const sellTime = form.sellTime.length === 16 ? form.sellTime + ':00' : form.sellTime
  emit('saved', {
    itemId: selectedItem.value?.id,
    itemName: selectedItem.value?.marketHashName ?? manualName.value.trim(),
    quantity: Number(form.quantity) || 1,
    exterior: form.exterior || undefined,
    floatValue,
    buyPrice: Number(form.buyPrice),
    buyTime,
    buyPlatform: form.buyPlatform,
    note: form.note || undefined,
    sellPrice: props.editing && form.sellPrice !== '' ? Number(form.sellPrice) : undefined,
    sellTime: props.editing && form.sellTime ? sellTime : undefined,
    sellPlatform: props.editing && form.sellPlatform ? form.sellPlatform : undefined,
    fee: props.editing && form.fee !== '' ? Number(form.fee) : undefined
  })
}
</script>

<template>
  <div class="dialog-mask" @click.self="emit('close')">
    <div class="dialog-panel form-panel" role="dialog" aria-modal="true" aria-label="买入记录">
      <form novalidate @submit.prevent="submit">
        <div class="form-header">
          <h2>{{ props.editing ? '编辑记录（含卖出信息）' : '新增买入记录' }}</h2>
          <button type="button" class="close-btn" aria-label="关闭" @click="emit('close')">×</button>
        </div>

        <div class="section-label">买入信息</div>
        <div class="grid">
          <div class="field wide">
            <span>饰品（数据字典） <i class="req">*</i></span>
            <ItemSelect v-model="selectedItem" placeholder="输入关键词搜索饰品（支持中文）" />
            <p class="field-error" v-if="errors.item">{{ errors.item }}</p>
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

          <div class="field">
            <span>数量</span>
            <input v-model="form.quantity" class="input num" type="number" step="0.0001" min="0.0001" />
          </div>
          <div class="field" :class="{ 'field-attention': props.attention }">
            <span>买入价 <i class="req">*</i></span>
            <input ref="firstField" v-model="form.buyPrice" class="input num" type="number" step="0.01" min="0" @input="errors.buyPrice = undefined" />
            <p v-if="errors.buyPrice" class="field-error">{{ errors.buyPrice }}</p>
            <p v-else-if="props.attention" class="field-hint attention-hint">该批次买入价缺失（当前为 0），请补填后盈亏才准确</p>
          </div>
          <div class="field">
            <span>买入时间 <i class="req">*</i></span>
            <input v-model="form.buyTime" class="input" type="datetime-local" />
          </div>
          <div class="field">
            <span>买入平台 <i class="req">*</i></span>
            <select v-model="form.buyPlatform" class="select">
              <option value="steam">Steam</option>
              <option value="uu">UU</option>
              <option value="buff">BUFF</option>
            </select>
          </div>
          <div class="field wide">
            <span>备注</span>
            <input v-model="form.note" class="input" />
          </div>
        </div>

        <template v-if="props.editing">
          <div class="section-label">卖出信息 <span class="section-hint">填写出售价后按「实际收入 = 出售价 − 手续费」计算盈亏</span></div>
          <div class="grid">
            <div class="field">
              <span>出售价</span>
              <input v-model="form.sellPrice" class="input num" type="number" step="0.01" min="0" @input="errors.sellPrice = undefined" />
              <p v-if="errors.sellPrice" class="field-error">{{ errors.sellPrice }}</p>
            </div>
            <div class="field">
              <span>手续费</span>
              <input v-model="form.fee" class="input num" type="number" step="0.01" min="0" @input="errors.fee = undefined" />
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
        </template>

        <div class="form-actions">
          <div class="total-preview" v-if="totalPreview !== null">
            买入成本 ≈ <b class="num">{{ formatMoney(totalPreview) }}</b>
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
.form-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px 12px; border-bottom: 1px solid var(--border); }
.form-header h2 { margin: 0; }
.close-btn { border: none; background: none; font-size: 20px; line-height: 1; color: var(--text-muted); cursor: pointer; padding: 4px 8px; border-radius: var(--radius-sm); }
.close-btn:hover { color: var(--text); background: rgba(16,24,40,.06); }
.section-label { padding: 14px 20px 0; font-size: 12px; font-weight: 650; color: var(--text-secondary); }
.section-hint { font-weight: 400; color: var(--text-muted); margin-left: 6px; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; padding: 12px 20px; }
.field .req { font-style: normal; }
.field-attention .input { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(37,99,235,.14); }
.attention-hint { color: var(--accent); font-weight: 550; }
.field-hint { font-size: 11px; color: var(--text-muted); margin: 0; }
.form-actions { display: flex; gap: 8px; justify-content: space-between; align-items: center; padding: 14px 20px; border-top: 1px solid var(--border); background: var(--surface-muted); border-radius: 0 0 var(--radius-lg) var(--radius-lg); }
.total-preview { font-size: 13px; color: var(--text-secondary); }
.actions-right { display: flex; gap: 8px; }
@media (max-width: 640px) { .grid { grid-template-columns: 1fr; } }
</style>