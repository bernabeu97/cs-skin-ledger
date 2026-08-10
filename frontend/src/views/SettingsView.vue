<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useSettingsStore } from '../stores/settings'
import { useLotsStore } from '../stores/lots'
import { useUiStore } from '../stores/ui'
import { errorMessage } from '../api/client'

const settingsStore = useSettingsStore()
const lotsStore = useLotsStore()
const ui = useUiStore()

const form = reactive({ steam: 0.15, uu: 0.005, buff: 0.025 })
const saving = ref(false)
const saveMsg = ref('')
const token = ref('')
const tokenPending = ref(false)
const tokenError = ref('')

function percentToRate(v: number): number {
  return Math.round((v / 100) * 100000) / 100000
}

onMounted(async () => {
  await Promise.all([settingsStore.loadFees(), settingsStore.loadTokenStatus(), lotsStore.loadPriceConfig()])
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

async function bindToken() {
  if (tokenPending.value || !token.value.trim()) return
  tokenPending.value = true
  tokenError.value = ''
  try {
    await settingsStore.saveToken(token.value)
    token.value = ''
    await lotsStore.loadPriceConfig()
    ui.toast('success', 'CSQAQ Token 已安全绑定')
  } catch (e) {
    tokenError.value = errorMessage(e)
  } finally {
    tokenPending.value = false
  }
}

async function unbindToken() {
  if (!window.confirm('确定解绑当前账号的 CSQAQ Token？解绑后可能无法刷新 UU 行情。')) return
  tokenPending.value = true
  tokenError.value = ''
  try {
    await settingsStore.deleteToken()
    await lotsStore.loadPriceConfig()
    ui.toast('success', '账号 Token 已解绑')
  } catch (e) {
    tokenError.value = errorMessage(e)
  } finally {
    tokenPending.value = false
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

    <div class="card form-panel token-panel">
      <div class="section-heading">
        <div>
          <h3>CSQAQ 行情 Token</h3>
          <p class="page-desc">用于获取 UU 市场价。Token 由后端加密保存，页面不会再次显示明文。</p>
        </div>
        <span :class="settingsStore.tokenStatus?.configured ? 'status-pill ok' : 'status-pill'">
          {{ settingsStore.tokenStatus?.configured ? '已绑定' : '未绑定' }}
        </span>
      </div>

      <div v-if="settingsStore.tokenStatus?.configured" class="token-current">
        <div>
          <span class="token-label">当前凭据</span>
          <code>{{ settingsStore.tokenStatus.maskedToken }}</code>
          <span class="token-source">{{ settingsStore.tokenStatus.source === 'account' ? '当前账号' : '服务器默认' }}</span>
        </div>
        <button v-if="settingsStore.tokenStatus.source === 'account'" type="button" class="btn btn-danger" :disabled="tokenPending" @click="unbindToken">解绑</button>
      </div>

      <form class="token-form" @submit.prevent="bindToken">
        <label class="field token-field">
          <span>{{ settingsStore.tokenStatus?.configured ? '替换 Token' : '绑定 Token' }}</span>
          <input v-model.trim="token" class="input token-input" type="password" autocomplete="off" minlength="8" maxlength="128" placeholder="粘贴 CSQAQ ApiToken" required />
          <p class="field-hint">先在 CSQAQ 个人中心把当前设备的公网 IP 加入白名单，再保存 Token。</p>
        </label>
        <button class="btn btn-primary bind-button" type="submit" :disabled="tokenPending || !token.trim()">
          {{ tokenPending ? '保存中…' : settingsStore.tokenStatus?.configured ? '替换 Token' : '绑定 Token' }}
        </button>
      </form>
      <p v-if="tokenError" class="inline-error" role="alert">{{ tokenError }}</p>
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
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.status-pill { flex: none; padding: 4px 9px; border-radius: 999px; color: var(--text-secondary); background: #eef0f3; font-size: 11px; font-weight: 650; }
.status-pill.ok { color: #176b43; background: #e8f7ef; }
.token-current { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 0; margin-bottom: 14px; border-top: 1px solid var(--border); border-bottom: 1px solid var(--border); }
.token-current > div { display: flex; align-items: center; gap: 9px; min-width: 0; }
.token-label, .token-source { color: var(--text-muted); font-size: 11px; }
.token-current code { color: var(--text); font-size: 13px; }
.token-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end; gap: 12px; }
.token-field { min-width: 0; }
.token-input { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.bind-button { min-width: 112px; margin-bottom: 20px; }
.inline-error { margin: 10px 0 0; padding: 8px 10px; color: var(--danger); background: #fff1f2; border: 1px solid #fecdd3; border-radius: var(--radius-sm); font-size: 12px; }
@media (max-width: 760px) { .form-grid { grid-template-columns: 1fr; } }
@media (max-width: 620px) {
  .token-form { grid-template-columns: 1fr; }
  .bind-button { width: 100%; margin-bottom: 0; }
  .token-current { align-items: flex-start; }
  .token-current > div { align-items: flex-start; flex-direction: column; gap: 3px; }
}
</style>
