import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api/client'
import { downloadBlob } from '../utils/format'
import type { CostRequest, CostSummary, OtherCost } from '../types'

export interface CostQuery {
  category?: string
  direction?: string
  from?: string
  to?: string
}

export const useCostsStore = defineStore('costs', () => {
  const costs = ref<OtherCost[]>([])
  const summary = ref<CostSummary | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function loadCosts(query: CostQuery = {}) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<OtherCost[]>('/costs', { params: query })
      costs.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function loadSummary() {
    try {
      const { data } = await api.get<CostSummary>('/costs/summary')
      summary.value = data
    } catch (e) {
      error.value = errorMessage(e)
    }
  }

  async function createCost(payload: CostRequest) {
    await api.post<OtherCost>('/costs', payload)
    await Promise.all([loadCosts(), loadSummary()])
  }

  async function updateCost(id: number, payload: CostRequest) {
    await api.put<OtherCost>(`/costs/${id}`, payload)
    await Promise.all([loadCosts(), loadSummary()])
  }

  async function deleteCost(id: number) {
    await api.delete(`/costs/${id}`)
    await Promise.all([loadCosts(), loadSummary()])
  }

  async function exportCosts(format: 'csv' | 'json' | 'xlsx') {
    const { data } = await api.get<Blob>('/costs/export', {
      params: { format },
      responseType: 'blob'
    })
    downloadBlob(data, `costs.${format === 'xlsx' ? 'xlsx' : format}`)
  }

  return {
    costs, summary, loading, error,
    loadCosts, loadSummary, createCost, updateCost, deleteCost, exportCosts
  }
})