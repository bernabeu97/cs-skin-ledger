<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const setup = ref<{ manualKey: string; provisioningUri: string } | null>(null)
const recoveryCodes = ref<string[]>([])
const totpCode = ref('')
const totpPending = ref(false)
const passwordPending = ref(false)
const error = ref('')
const password = reactive({ current: '', next: '', confirm: '' })
const setupNeeded = computed(() => auth.mfaSetupRequired && !auth.totpEnabled)

onMounted(async () => {
  if (!setupNeeded.value) return
  try {
    setup.value = await auth.setupTotp()
  } catch (e) {
    error.value = errorMessage(e)
  }
})

async function confirmTotp() {
  if (totpPending.value) return
  totpPending.value = true
  error.value = ''
  try {
    recoveryCodes.value = await auth.confirmTotp(totpCode.value)
    ui.toast('success', '双重验证已启用')
    if (!auth.passwordChangeRequired) await router.replace('/')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    totpPending.value = false
  }
}

async function changePassword() {
  error.value = ''
  if (password.next !== password.confirm) {
    error.value = '两次输入的新密码不一致'
    return
  }
  passwordPending.value = true
  try {
    await auth.changePassword(password.current, password.next)
    window.location.assign('/login?passwordChanged=1')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    passwordPending.value = false
  }
}

async function copy(text: string) {
  await navigator.clipboard.writeText(text)
  ui.toast('success', '已复制')
}
</script>

<template>
  <div class="security-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">ACCOUNT SECURITY</p>
        <h1>账号安全</h1>
        <p>管理密码和双重验证。管理员必须完成验证器绑定后才能使用业务功能。</p>
      </div>
      <span :class="auth.totpEnabled ? 'badge badge-success' : 'badge badge-muted'">{{ auth.totpEnabled ? '双重验证已开启' : '双重验证未开启' }}</span>
    </div>

    <p v-if="error" class="error-banner" role="alert"><span>{{ error }}</span></p>

    <section v-if="setupNeeded || recoveryCodes.length" class="card security-card" aria-labelledby="totp-title">
      <div class="step-index">01</div>
      <div class="step-content">
        <h2 id="totp-title">绑定身份验证器</h2>
        <p v-if="!recoveryCodes.length">在 Microsoft Authenticator、Google Authenticator 或其他 TOTP 应用中添加账号，再输入当前的 6 位验证码。</p>
        <template v-if="setup && !recoveryCodes.length">
          <div class="secret-block">
            <span>手动密钥</span>
            <code>{{ setup.manualKey }}</code>
            <button class="btn btn-sm" type="button" @click="copy(setup.manualKey)">复制</button>
          </div>
          <details>
            <summary>查看配置 URI</summary>
            <code class="uri">{{ setup.provisioningUri }}</code>
          </details>
          <form class="inline-form" @submit.prevent="confirmTotp">
            <label class="field">
              <span>6 位验证码</span>
              <input v-model.trim="totpCode" class="input mono" inputmode="numeric" autocomplete="one-time-code" pattern="\d{6}" maxlength="6" required autofocus />
            </label>
            <button class="btn btn-primary" type="submit" :disabled="totpPending">{{ totpPending ? '验证中…' : '验证并启用' }}</button>
          </form>
        </template>
        <div v-else-if="recoveryCodes.length" class="recovery-box">
          <strong>请立即保存恢复码</strong>
          <p>每个恢复码只能使用一次。关闭页面后将无法再次查看明文。</p>
          <div class="recovery-grid"><code v-for="code in recoveryCodes" :key="code">{{ code }}</code></div>
          <button class="btn" type="button" @click="copy(recoveryCodes.join('\n'))">复制全部恢复码</button>
        </div>
        <p v-else class="muted">正在准备安全密钥…</p>
      </div>
    </section>

    <section class="card security-card" aria-labelledby="password-title">
      <div class="step-index">{{ setupNeeded ? '02' : '01' }}</div>
      <div class="step-content">
        <h2 id="password-title">{{ auth.passwordChangeRequired ? '设置新密码' : '修改密码' }}</h2>
        <p>{{ auth.passwordChangeRequired ? '管理员已重置此账号密码，继续使用前必须设置只有你知道的新密码。' : '修改后所有设备上的会话都会失效，需要重新登录。' }}</p>
        <form class="password-form" @submit.prevent="changePassword">
          <label class="field"><span>当前密码</span><input v-model="password.current" class="input" type="password" autocomplete="current-password" required /></label>
          <label class="field"><span>新密码</span><input v-model="password.next" class="input" type="password" autocomplete="new-password" minlength="12" maxlength="128" required /><small>至少 12 位，请使用独立密码</small></label>
          <label class="field"><span>确认新密码</span><input v-model="password.confirm" class="input" type="password" autocomplete="new-password" minlength="12" maxlength="128" required /></label>
          <button class="btn btn-primary" type="submit" :disabled="passwordPending">{{ passwordPending ? '保存中…' : '保存并重新登录' }}</button>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.security-page { max-width: 820px; margin: 0 auto; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.page-heading h1 { margin: 2px 0 5px; font-size: 25px; }
.page-heading p:not(.eyebrow) { margin: 0; color: var(--text-secondary); }
.eyebrow { margin: 0; color: var(--accent); font: 700 10px/1 var(--font-mono); letter-spacing: .14em; }
.security-card { display: grid; grid-template-columns: 50px 1fr; gap: 16px; padding: 22px; margin-bottom: 14px; }
.step-index { display: grid; place-items: center; width: 38px; height: 38px; color: var(--accent); background: var(--accent-soft); border-radius: 50%; font: 700 12px/1 var(--font-mono); }
.step-content h2 { margin: 1px 0 5px; font-size: 17px; }
.step-content > p { margin: 0 0 18px; color: var(--text-secondary); font-size: 13px; }
.secret-block { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 10px; padding: 12px; border: 1px solid var(--border); border-radius: var(--radius); background: var(--surface-muted); }
.secret-block span { color: var(--text-muted); font-size: 11px; }
.secret-block code { overflow-wrap: anywhere; color: var(--text); font-size: 14px; letter-spacing: .08em; }
details { margin: 9px 0 16px; color: var(--text-secondary); font-size: 12px; }
.uri { display: block; margin-top: 8px; padding: 9px; overflow-wrap: anywhere; background: var(--surface-muted); border-radius: var(--radius-sm); }
.inline-form { display: grid; grid-template-columns: minmax(180px, 240px) auto; align-items: end; gap: 10px; }
.password-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: end; gap: 13px; }
.password-form label:first-child { grid-column: 1 / -1; }
.field small { color: var(--text-muted); font-size: 11px; }
.recovery-box { padding: 16px; border: 1px solid #f0c36a; border-radius: var(--radius); background: #fff9e9; color: #563b00; }
.recovery-box p { margin: 5px 0 12px; font-size: 12px; }
.recovery-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 7px; margin-bottom: 14px; }
.recovery-grid code { padding: 6px 8px; background: rgba(255,255,255,.65); border-radius: 4px; }
.muted { color: var(--text-muted); }
@media (max-width: 620px) {
  .page-heading { flex-direction: column; }
  .security-card { grid-template-columns: 1fr; padding: 18px; }
  .inline-form, .password-form { grid-template-columns: 1fr; }
  .password-form label:first-child { grid-column: auto; }
  .recovery-grid { grid-template-columns: 1fr; }
}
</style>
