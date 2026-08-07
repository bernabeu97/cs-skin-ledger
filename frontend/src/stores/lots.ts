import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api/client'
import { downloadBlob } from '../utils/format'
import type { Lot, LotCreateRequest, LotSellRequest, LotSummary, PnlRow } from '../types'

export interface LotQuery {
  q?: string
  status?: string
  platform?: string
  from?: string
  to?: string
}

export const useLotsStore = defineStore('lots', () => {
  const lots = ref<Lot[]>([])
  const summary = ref<LotSummary | null>(null)
  const pnlRows = ref<PnlRow[]>([])
  const loading = ref(false)
  const loadingSummary = ref(false)
  const loadingPnl = ref(false)
  const error = ref('')
  const dashError = ref('')

  async function loadLots(query: LotQuery = {}) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<Lot[]>('/lots', { params: query })
      lots.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function loadSummary() {
    loadingSummary.value = true
    try {
      const { data } = await api.get<LotSummary>('/lots/summary')
      summary.value = data
    } catch (e) {
      dashError.value = errorMessage(e)
    } finally {
      loadingSummary.value = false
    }
  }

  async function loadPnl(groupBy: string) {
    loadingPnl.value = true
    try {
      const { data } = await api.get<PnlRow[]>('/lots/pnl', { params: { group_by: groupBy } })
      pnlRows.value = data
    } catch (e) {
      dashError.value = errorMessage(e)
    } finally {
      loadingPnl.value = false
    }
  }

  async function createLot(payload: LotCreateRequest) {
    await api.post<Lot>('/lots', payload)
  }

  async function updateLot(id: number, payload: LotCreateRequest) {
    await api.put<Lot>(`/lots/${id}`, payload)
  }

  async function sellLot(id: number, payload: LotSellRequest) {
    await api.post<Lot>(`/lots/${id}/sell`, payload)
  }

  async function deleteLot(id: number) {
    await api.delete(`/lots/${id}`)
  }

  async function exportLots(format: 'csv' | 'json' | 'xlsx') {
    const { data } = await api.get<Blob>('/lots/export', {
      params: { format },
      responseType: 'blob'
    })
    downloadBlob(data, `lots.${format === 'xlsx' ? 'xlsx' : format}`)
  }

  return {
    lots, summary, pnlRows, loading, loadingSummary, loadingPnl, error, dashError,
    loadLots, loadSummary, loadPnl, createLot, updateLot, sellLot, deleteLot, exportLots
  }
})