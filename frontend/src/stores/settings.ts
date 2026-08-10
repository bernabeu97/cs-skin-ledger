import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api/client'
import type { CsqaqTokenStatus, FeeSettings } from '../types'

export const useSettingsStore = defineStore('settings', () => {
  const fees = ref<FeeSettings | null>(null)
  const loading = ref(false)
  const error = ref('')
  const tokenStatus = ref<CsqaqTokenStatus | null>(null)

  async function loadFees() {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<FeeSettings>('/settings/fees')
      fees.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function saveFees(payload: FeeSettings) {
    const { data } = await api.put<FeeSettings>('/settings/fees', payload)
    fees.value = data
  }

  async function loadTokenStatus() {
    const { data } = await api.get<CsqaqTokenStatus>('/settings/csqaq-token')
    tokenStatus.value = data
  }

  async function saveToken(token: string) {
    const { data } = await api.put<CsqaqTokenStatus>('/settings/csqaq-token', { token })
    tokenStatus.value = data
  }

  async function deleteToken() {
    const { data } = await api.delete<CsqaqTokenStatus>('/settings/csqaq-token')
    tokenStatus.value = data
  }

  /** 某平台费率（0.005 = 0.5%），未知平台返回 0 */
  function rateFor(platform: string): number {
    if (!fees.value) return 0
    if (platform === 'steam') return fees.value.steam
    if (platform === 'uu') return fees.value.uu
    if (platform === 'buff') return fees.value.buff
    return 0
  }

  return { fees, loading, error, tokenStatus, loadFees, saveFees, loadTokenStatus, saveToken, deleteToken, rateFor }
})
