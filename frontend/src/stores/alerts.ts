import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api/client'
import type { PriceAlert } from '../types'

export interface AlertCreatePayload {
  itemId: number
  platform: string
  condition: 'gt' | 'lt'
  threshold: number
}

export const useAlertsStore = defineStore('alerts', () => {
  const alerts = ref<PriceAlert[]>([])
  const loading = ref(false)
  const error = ref('')

  async function loadAlerts() {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<PriceAlert[]>('/alerts')
      alerts.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function createAlert(payload: AlertCreatePayload) {
    await api.post<PriceAlert>('/alerts', payload)
    await loadAlerts()
  }

  async function deleteAlert(id: number) {
    await api.delete(`/alerts/${id}`)
    await loadAlerts()
  }

  async function resetAlert(id: number) {
    await api.post<PriceAlert>(`/alerts/${id}/reset`)
    await loadAlerts()
  }

  return { alerts, loading, error, loadAlerts, createAlert, deleteAlert, resetAlert }
})