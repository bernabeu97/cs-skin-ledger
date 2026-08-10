import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, ensureCsrf, errorMessage } from '../api/client'

interface AuthView {
  authenticated: boolean
  username: string | null
}

export const useAuthStore = defineStore('auth', () => {
  const authenticated = ref(false)
  const username = ref<string | null>(null)
  const ready = ref(false)

  function apply(view: AuthView) {
    authenticated.value = view.authenticated
    username.value = view.username
  }

  async function load() {
    if (ready.value) return
    try {
      const { data } = await api.get<AuthView>('/auth/me')
      apply(data)
      await ensureCsrf()
    } catch {
      apply({ authenticated: false, username: null })
    } finally {
      ready.value = true
    }
  }

  async function login(account: string, password: string) {
    try {
      const { data } = await api.post<AuthView>('/auth/login', { username: account, password })
      apply(data)
      await ensureCsrf()
    } catch (e) {
      throw new Error(errorMessage(e))
    }
  }

  async function register(account: string, password: string) {
    try {
      const { data } = await api.post<AuthView>('/auth/register', { username: account, password })
      apply(data)
      await ensureCsrf()
    } catch (e) {
      throw new Error(errorMessage(e))
    }
  }

  async function logout() {
    await ensureCsrf()
    await api.post('/auth/logout')
    apply({ authenticated: false, username: null })
    window.location.assign('/login')
  }

  return { authenticated, username, ready, load, login, register, logout }
})
