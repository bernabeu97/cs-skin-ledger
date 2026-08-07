import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage, type TradeQuery } from '../api/client'
import { downloadBlob } from '../utils/format'
import type { HoldingRow, ImportResult, PnlRow, Trade, TradeCreateRequest } from '../types'

export const useTradesStore = defineStore('trades', () => {
  const trades = ref<Trade[]>([])
  const holdings = ref<HoldingRow[]>([])
  const pnlRows = ref<PnlRow[]>([])
  const totalCost = ref(0)
  const totalRealizedPnl = ref(0)
  const loading = ref(false)
  const loadingPortfolio = ref(false)
  const loadingPnl = ref(false)
  const error = ref('')
  const dashError = ref('')

  async function loadTrades(query: TradeQuery = {}) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<Trade[]>('/trades', { params: query })
      trades.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function loadPortfolio() {
    loadingPortfolio.value = true
    dashError.value = ''
    try {
      const { data } = await api.get<{
        totalCost: number
        totalRealizedPnl: number
        holdings: HoldingRow[]
      }>('/analytics/portfolio')
      totalCost.value = data.totalCost
      totalRealizedPnl.value = data.totalRealizedPnl
      holdings.value = data.holdings
    } catch (e) {
      dashError.value = errorMessage(e)
    } finally {
      loadingPortfolio.value = false
    }
  }

  async function loadPnl(groupBy: string) {
    loadingPnl.value = true
    dashError.value = ''
    try {
      const { data } = await api.get<PnlRow[]>('/analytics/pnl', { params: { group_by: groupBy } })
      pnlRows.value = data
    } catch (e) {
      dashError.value = errorMessage(e)
    } finally {
      loadingPnl.value = false
    }
  }

  async function createTrade(payload: TradeCreateRequest) {
    await api.post<Trade>('/trades', payload)
  }

  async function updateTrade(id: number, payload: TradeCreateRequest) {
    await api.put<Trade>(`/trades/${id}`, payload)
  }

  async function deleteTrade(id: number) {
    await api.delete(`/trades/${id}`)
  }

  async function importCsv(file: File) {
    const fd = new FormData()
    fd.append('file', file)
    const { data } = await api.post<ImportResult>('/trades/import/csv', fd)
    return data
  }

  async function exportTrades(format: 'csv' | 'json' | 'xlsx') {
    const { data } = await api.get<Blob>('/trades/export', {
      params: { format },
      responseType: 'blob'
    })
    downloadBlob(data, `trades.${format === 'xlsx' ? 'xlsx' : format}`)
  }

  return {
    trades, holdings, pnlRows, totalCost, totalRealizedPnl,
    loading, loadingPortfolio, loadingPnl, error, dashError,
    loadTrades, loadPortfolio, loadPnl, createTrade, updateTrade, deleteTrade,
    importCsv, exportTrades
  }
})