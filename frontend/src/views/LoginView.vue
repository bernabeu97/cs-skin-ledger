<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const form = reactive({ username: '', password: '', confirmPassword: '', inviteCode: '', totpCode: '' })
const pending = ref(false)
const error = ref('')
const isRegister = computed(() => mode.value === 'register')

function switchMode(next: 'login' | 'register') {
  mode.value = next
  error.value = ''
  form.password = ''
  form.confirmPassword = ''
  form.totpCode = ''
}

async function submit() {
  if (pending.value) return
  error.value = ''
  if (isRegister.value && form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致'
    return
  }
  pending.value = true
  try {
    if (isRegister.value) {
      await auth.register(form.username, form.password, form.inviteCode)
    } else {
      await auth.login(form.username, form.password, form.totpCode)
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(auth.mfaSetupRequired || auth.passwordChangeRequired ? '/security' : redirect)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-shell" aria-labelledby="auth-title">
      <div class="auth-intro">
        <p class="eyebrow">SKINLEDGER</p>
        <h1>把每一笔饰品交易，算得清清楚楚。</h1>
        <p>私有账本、实时持仓和行情分析集中在一个界面。实例仅接受管理员发放的邀请码。</p>
        <div class="trust-row" aria-label="安全特性">
          <span>邀请制</span><span>独立账本</span><span>双重验证</span>
        </div>
      </div>

      <div class="auth-panel">
        <div class="auth-brand">CS 饰品账本</div>
        <h2 id="auth-title">{{ isRegister ? '创建账号' : '欢迎回来' }}</h2>
        <p class="auth-desc">{{ isRegister ? '使用管理员发放的一次性邀请码注册。' : '登录后继续管理你的饰品资产。' }}</p>

        <div class="auth-tabs" role="tablist" aria-label="账号操作">
          <button type="button" :class="{ active: mode === 'login' }" role="tab" :aria-selected="mode === 'login'" @click="switchMode('login')">登录</button>
          <button type="button" :class="{ active: mode === 'register' }" role="tab" :aria-selected="mode === 'register'" @click="switchMode('register')">邀请码注册</button>
        </div>

        <form class="auth-form" @submit.prevent="submit">
          <label v-if="isRegister" class="field">
            <span>邀请码</span>
            <input v-model.trim="form.inviteCode" class="input mono" autocomplete="one-time-code" maxlength="64" placeholder="XXXXXX-XXXXXX-XXXXXX" required autofocus />
          </label>
          <label class="field">
            <span>用户名</span>
            <input v-model.trim="form.username" class="input" autocomplete="username" minlength="3" maxlength="32" required :autofocus="!isRegister" />
            <small v-if="isRegister">3–32 个文字、数字、下划线或短横线</small>
          </label>
          <label class="field">
            <span>密码</span>
            <input v-model="form.password" class="input" type="password" :autocomplete="isRegister ? 'new-password' : 'current-password'" :minlength="isRegister ? 12 : 1" maxlength="128" required />
            <small v-if="isRegister">至少 12 位，请勿与其他网站共用</small>
          </label>
          <label v-if="isRegister" class="field">
            <span>确认密码</span>
            <input v-model="form.confirmPassword" class="input" type="password" autocomplete="new-password" minlength="12" maxlength="128" required />
          </label>
          <label v-else class="field">
            <span>双重验证码 <em>已启用时填写</em></span>
            <input v-model.trim="form.totpCode" class="input mono" inputmode="numeric" autocomplete="one-time-code" maxlength="32" placeholder="6 位验证码或恢复码" />
          </label>

          <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
          <button class="btn btn-primary auth-submit" type="submit" :disabled="pending">
            {{ pending ? '处理中…' : isRegister ? '创建账号' : '登录账本' }}
          </button>
        </form>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; padding: 28px 20px; background: radial-gradient(circle at 12% 8%, #172554 0, #0b1220 32%, #080b12 72%); }
.auth-shell { width: min(100%, 940px); display: grid; grid-template-columns: 1.15fr .85fr; overflow: hidden; border: 1px solid rgba(255,255,255,.1); border-radius: 18px; background: rgba(15,23,42,.76); box-shadow: 0 28px 90px rgba(0,0,0,.38); }
.auth-intro { display: flex; flex-direction: column; justify-content: center; padding: 54px; color: #fff; background: linear-gradient(145deg, rgba(37,99,235,.24), rgba(15,23,42,.25)); }
.eyebrow { margin: 0 0 18px; color: #93c5fd; font: 700 12px/1 var(--font-mono); letter-spacing: .18em; }
.auth-intro h1 { max-width: 520px; margin: 0 0 16px; font-size: clamp(30px, 4.2vw, 48px); line-height: 1.13; letter-spacing: -.04em; }
.auth-intro > p:not(.eyebrow) { max-width: 500px; margin: 0; color: #b9c3d5; font-size: 15px; }
.trust-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 34px; }
.trust-row span { padding: 5px 9px; border: 1px solid rgba(147,197,253,.28); border-radius: 999px; color: #cbdcf8; font-size: 11px; }
.auth-panel { padding: 38px 34px; background: var(--surface); }
.auth-brand { color: var(--accent); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
h2 { margin: 8px 0 5px; font-size: 25px; }
.auth-desc { margin: 0 0 20px; color: var(--text-secondary); font-size: 13px; }
.auth-tabs { display: grid; grid-template-columns: 1fr 1fr; padding: 3px; margin-bottom: 20px; border-radius: 9px; background: var(--surface-muted); }
.auth-tabs button { min-height: 38px; border: 0; border-radius: 7px; color: var(--text-secondary); background: transparent; cursor: pointer; font-weight: 600; }
.auth-tabs button.active { color: var(--text); background: var(--surface); box-shadow: var(--shadow-sm); }
.auth-tabs button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.auth-form { display: grid; gap: 14px; }
.field small, .field em { color: var(--text-muted); font-size: 11px; font-style: normal; font-weight: 400; }
.auth-error { margin: 0; padding: 9px 11px; color: var(--danger); background: var(--danger-soft); border: 1px solid color-mix(in srgb, var(--danger) 25%, transparent); border-radius: var(--radius-sm); font-size: 12px; }
.auth-submit { width: 100%; min-height: 42px; margin-top: 2px; }
@media (max-width: 760px) {
  .auth-page { padding: 18px 12px; }
  .auth-shell { grid-template-columns: 1fr; }
  .auth-intro { padding: 28px 24px; }
  .auth-intro h1 { font-size: 27px; }
  .auth-intro > p:not(.eyebrow), .trust-row { display: none; }
  .auth-panel { padding: 28px 22px; }
}
</style>
