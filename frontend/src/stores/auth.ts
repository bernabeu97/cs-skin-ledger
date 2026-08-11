import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { api, ensureCsrf, errorMessage } from '../api/client'

export interface AuthView {
  authenticated: boolean
  username: string | null
  role: 'ADMIN' | 'USER' | null
  totpEnabled: boolean
  mfaSetupRequired: boolean
  passwordChangeRequired: boolean
}

const anonymous: AuthView = {
  authenticated: false,
  username: null,
  role: null,
  totpEnabled: false,
  mfaSetupRequired: false,
  passwordChangeRequired: false
}

export const useAuthStore = defineStore('auth', () => {
  const authenticated = ref(false)
  const username = ref<string | null>(null)
  const role = ref<AuthView['role']>(null)
  const totpEnabled = ref(false)
  const mfaSetupRequired = ref(false)
  const passwordChangeRequired = ref(false)
  const ready = ref(false)
  const isAdmin = computed(() => role.value === 'ADMIN')

  function apply(view: AuthView) {
    authenticated.value = view.authenticated
    username.value = view.username
    role.value = view.role
    totpEnabled.value = view.totpEnabled
    mfaSetupRequired.value = view.mfaSetupRequired
    passwordChangeRequired.value = view.passwordChangeRequired
  }

  async function refresh() {
    const { data } = await api.get<AuthView>('/auth/me')
    apply(data)
    await ensureCsrf()
  }

  async function load() {
    if (ready.value) return
    try {
      await refresh()
    } catch {
      apply(anonymous)
    } finally {
      ready.value = true
    }
  }

  async function login(account: string, password: string, totpCode?: string) {
    try {
      const { data } = await api.post<AuthView>('/auth/login', {
        username: account,
        password,
        totpCode: totpCode?.trim() || null
      })
      apply(data)
      await ensureCsrf()
    } catch (e) {
      throw new Error(errorMessage(e))
    }
  }

  async function register(account: string, password: string, inviteCode: string) {
    try {
      const { data } = await api.post<AuthView>('/auth/register', {
        username: account,
        password,
        inviteCode
      })
      apply(data)
      await ensureCsrf()
    } catch (e) {
      throw new Error(errorMessage(e))
    }
  }

  async function setupTotp() {
    await ensureCsrf()
    const { data } = await api.post<{ manualKey: string; provisioningUri: string }>('/auth/totp/setup')
    return data
  }

  async function confirmTotp(code: string) {
    await ensureCsrf()
    const { data } = await api.post<{ recoveryCodes: string[] }>('/auth/totp/confirm', { code })
    await refresh()
    return data.recoveryCodes
  }

  async function changePassword(currentPassword: string, newPassword: string) {
    await ensureCsrf()
    await api.post('/auth/password', { currentPassword, newPassword })
    apply(anonymous)
  }

  async function logout() {
    await ensureCsrf()
    await api.post('/auth/logout')
    apply(anonymous)
    window.location.assign('/login')
  }

  return {
    authenticated, username, role, totpEnabled, mfaSetupRequired, passwordChangeRequired,
    ready, isAdmin, load, refresh, login, register, setupTotp, confirmTotp, changePassword, logout
  }
})
