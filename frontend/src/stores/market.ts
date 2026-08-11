import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api/client'
import type { CsqaqIndex, CsqaqIndexKline, CsqaqKlinePeriod, MarketIndexView, PriceHistoryView, WatchlistItem } from '../types'

export type MarketPeriod = '24h' | '7d' | '30d' | '90d'
export type IndexKind = 'holdings' | 'watchlist'

export const useMarketStore = defineStore('market', () => {
  const watchlist = ref<WatchlistItem[]>([])
  const history = ref<PriceHistoryView | null>(null)
  const index = ref<MarketIndexView | null>(null)
  const csqaqIndices = ref<CsqaqIndex[]>([])
  const csqaqKline = ref<CsqaqIndexKline | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function loadWatchlist() {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<WatchlistItem[]>('/watchlist')
      watchlist.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function addWatch(itemId: number, exterior?: string) {
    await api.post('/watchlist', { itemId, exterior: exterior || null })
    await loadWatchlist()
  }

  async function deleteWatch(id: number) {
    await api.delete(`/watchlist/${id}`)
    await loadWatchlist()
  }

  async function loadHistory(itemId: number, exterior: string | null, period: MarketPeriod) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<PriceHistoryView>('/prices/history', {
        params: { itemId, exterior: exterior || undefined, period }
      })
      history.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function loadIndex(kind: IndexKind, period: MarketPeriod) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<MarketIndexView>('/prices/index', { params: { kind, period } })
      index.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function loadCsqaqIndices() {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<CsqaqIndex[]>('/prices/csqaq/indices')
      csqaqIndices.value = data
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  async function loadCsqaqKline(id: number, period: CsqaqKlinePeriod) {
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get<CsqaqIndexKline>('/prices/csqaq/index-kline', { params: { id, period } })
      csqaqKline.value = data
    } catch (e) {
      csqaqKline.value = null
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }

  return {
    watchlist, history, index, csqaqIndices, csqaqKline, loading, error,
    loadWatchlist, addWatch, deleteWatch, loadHistory, loadIndex, loadCsqaqIndices, loadCsqaqKline
  }
})
