<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const form = reactive({ username: '', password: '', confirmPassword: '' })
const pending = ref(false)
const error = ref('')

const isRegister = computed(() => mode.value === 'register')

function switchMode(next: 'login' | 'register') {
  mode.value = next
  error.value = ''
  form.password = ''
  form.confirmPassword = ''
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
      await auth.register(form.username, form.password)
    } else {
      await auth.login(form.username, form.password)
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-panel" aria-labelledby="auth-title">
      <div class="auth-brand">CS 饰品账本</div>
      <h1 id="auth-title">{{ isRegister ? '创建账本账号' : '登录你的账本' }}</h1>
      <p class="auth-desc">
        {{ isRegister ? '首次注册会自动接管本机原有账本数据。' : '登录后查看你的交易、盈亏和 UU 行情。' }}
      </p>

      <div class="auth-tabs" role="tablist" aria-label="账号操作">
        <button type="button" :class="{ active: mode === 'login' }" role="tab" @click="switchMode('login')">登录</button>
        <button type="button" :class="{ active: mode === 'register' }" role="tab" @click="switchMode('register')">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span>用户名</span>
          <input v-model.trim="form.username" class="input" autocomplete="username" minlength="3" maxlength="32" required autofocus />
          <small>3–32 个文字、数字、下划线或短横线</small>
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="form.password" class="input" type="password" :autocomplete="isRegister ? 'new-password' : 'current-password'" minlength="8" maxlength="72" required />
          <small v-if="isRegister">至少 8 位，请勿与其他网站共用密码</small>
        </label>
        <label v-if="isRegister" class="field">
          <span>确认密码</span>
          <input v-model="form.confirmPassword" class="input" type="password" autocomplete="new-password" minlength="8" maxlength="72" required />
        </label>

        <p v-if="error" class="auth-error" role="alert">{{ error }}</p>
        <button class="btn btn-primary auth-submit" type="submit" :disabled="pending">
          {{ pending ? '处理中…' : isRegister ? '注册并进入账本' : '登录' }}
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; padding: 28px 16px; background: #f4f5f7; }
.auth-panel { width: min(100%, 420px); padding: 30px; background: #fff; border: 1px solid var(--border); border-radius: 14px; box-shadow: 0 14px 40px rgba(17, 24, 39, .09); }
.auth-brand { color: var(--accent); font-size: 13px; font-weight: 700; letter-spacing: .04em; }
h1 { margin: 8px 0 6px; font-size: 26px; }
.auth-desc { margin: 0 0 22px; color: var(--text-secondary); font-size: 13px; }
.auth-tabs { display: grid; grid-template-columns: 1fr 1fr; padding: 3px; margin-bottom: 20px; border-radius: 9px; background: #eef0f3; }
.auth-tabs button { min-height: 38px; border: 0; border-radius: 7px; color: var(--text-secondary); background: transparent; cursor: pointer; font-weight: 600; }
.auth-tabs button.active { color: var(--text); background: #fff; box-shadow: 0 1px 3px rgba(17,24,39,.12); }
.auth-tabs button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.auth-form { display: grid; gap: 15px; }
.field small { color: var(--text-muted); font-size: 11px; }
.auth-error { margin: 0; padding: 9px 11px; color: var(--danger); background: #fff1f2; border: 1px solid #fecdd3; border-radius: var(--radius-sm); font-size: 12px; }
.auth-submit { width: 100%; min-height: 42px; margin-top: 2px; }
@media (max-width: 480px) { .auth-panel { padding: 24px 20px; } }
</style>
