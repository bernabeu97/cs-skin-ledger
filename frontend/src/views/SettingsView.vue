<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useSettingsStore } from '../stores/settings'
import { useLotsStore } from '../stores/lots'
import { useUiStore } from '../stores/ui'

const settingsStore = useSettingsStore()
const lotsStore = useLotsStore()
const ui = useUiStore()

const form = reactive({ steam: 0.15, uu: 0.005, buff: 0.025 })
const saving = ref(false)
const saveMsg = ref('')

function percentToRate(v: number): number {
  return Math.round((v / 100) * 100000) / 100000
}

onMounted(async () => {
  await Promise.all([settingsStore.loadFees(), lotsStore.loadPriceConfig()])
  if (settingsStore.fees) {
    form.steam = settingsStore.fees.steam * 100
    form.uu = settingsStore.fees.uu * 100
    form.buff = settingsStore.fees.buff * 100
  }
})

async function save() {
  if (saving.value) return
  saving.value = true
  saveMsg.value = ''
  try {
    await settingsStore.saveFees({
      steam: percentToRate(form.steam),
      uu: percentToRate(form.uu),
      buff: percentToRate(form.buff)
    })
    ui.toast('success', '费率配置已保存')
    saveMsg.value = '已保存，卖出表单会按新费率自动带出建议手续费'
  } catch (e) {
    ui.toast('error', String(e))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <h1>设置</h1>

    <div class="card form-panel">
      <h3>平台手续费率</h3>
      <p class="page-desc">填写各平台出售时的服务费率（百分比）。保存后，卖出表单会按「出售价 × 费率」自动带出建议手续费，可手动修改。</p>
      <div class="form-grid">
        <label class="field">
          <span>Steam 费率（%）</span>
          <input v-model.number="form.steam" class="input num" type="number" step="0.1" min="0" max="50" />
          <p class="field-hint">示例：Steam 社区市场约 15%</p>
        </label>
        <label class="field">
          <span>UU 费率（%）</span>
          <input v-model.number="form.uu" class="input num" type="number" step="0.1" min="0" max="50" />
          <p class="field-hint">悠悠有品按饰品档位收服务费，建议按你的实际档位填写</p>
        </label>
        <label class="field">
          <span>BUFF 费率（%）</span>
          <input v-model.number="form.buff" class="input num" type="number" step="0.1" min="0" max="50" />
          <p class="field-hint">BUFF 约 2.5%</p>
        </label>
      </div>
      <div class="form-actions">
        <p v-if="saveMsg" class="save-msg">{{ saveMsg }}</p>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存费率' }}
        </button>
      </div>
    </div>

    <div class="card form-panel">
      <h3>行情数据源</h3>
      <p class="page-desc">
        <span v-if="lotsStore.priceConfig?.csqaqConfigured" class="status-ok">CSQAQ 已配置（UU/Steam/BUFF 三平台行情）</span>
        <span v-else class="status-warn">CSQAQ 未配置，行情不可用。请在 <code>work/csqaq_token.txt</code> 配置 Token 后重启后端。</span>
      </p>
    </div>
  </div>
</template>

<style scoped>
.form-panel { padding: 18px 20px; margin-bottom: 16px; }
.form-panel h3 { margin: 0 0 6px; }
.page-desc { font-size: 13px; color: var(--text-secondary); margin: 0 0 14px; }
.form-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.field-hint { font-size: 11px; color: var(--text-muted); margin: 0; }
.form-actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 14px; }
.save-msg { font-size: 12px; color: var(--success); margin: 0; }
.status-ok { color: var(--success); font-weight: 550; }
.status-warn { color: #7a4f01; background: #fff7e6; border: 1px solid #f5c77b; border-radius: var(--radius-sm); padding: 8px 12px; display: inline-block; }
@media (max-width: 760px) { .form-grid { grid-template-columns: 1fr; } }
</style>