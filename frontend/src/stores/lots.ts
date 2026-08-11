import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api, errorMessage } from '../api/client'
import { downloadBlob } from '../utils/format'
import type { Lot, LotCreateRequest, LotImportResult, LotSellRequest, LotSummary, PnlRow, PortfolioValuation, PriceConfigView, PriceRefreshResult, UuFullJsonImportResult } from '../types'

export interface LotQuery {
  q?: string
  status?: string
  platform?: string
  from?: string
  to?: string
}

export const useLotsStore = defineStore('lots', () => {
  const lots = ref<Lot[]>([])
  const trash = ref<Lot[]>([])
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

  async function loadTrash() {
    const { data } = await api.get<Lot[]>('/lots/trash')
    trash.value = data
  }

  async function restoreLot(id: number) {
    await api.post(`/lots/${id}/restore`)
    await Promise.all([loadTrash(), loadLots(), loadSummary()])
  }

  async function purgeLot(id: number) {
    await api.delete(`/lots/${id}/purge`)
    await loadTrash()
  }

  async function downloadImportTemplate() {
    const { data } = await api.get<Blob>('/lots/import-template', { responseType: 'blob' })
    downloadBlob(data, 'skinledger-import-template.xlsx')
  }

  async function importWorkbook(file: File): Promise<LotImportResult> {
    const body = new FormData()
    body.append('file', file)
    const { data } = await api.post<LotImportResult>('/lots/import', body)
    await Promise.all([loadLots(), loadSummary(), loadValuation()])
    return data
  }

  // ===== 行情模块 =====
  const valuation = ref<PortfolioValuation | null>(null)
  const priceConfig = ref<PriceConfigView | null>(null)
  const refreshingPrices = ref(false)
  const loadingValuation = ref(false)

  async function loadValuation() {
    loadingValuation.value = true
    try {
      const { data } = await api.get<PortfolioValuation>('/prices/valuation')
      valuation.value = data
    } catch (e) {
      dashError.value = errorMessage(e)
    } finally {
      loadingValuation.value = false
    }
  }

  async function loadPriceConfig() {
    try {
      const { data } = await api.get<PriceConfigView>('/prices/config')
      priceConfig.value = data
    } catch {
      // 配置接口失败不阻塞页面
    }
  }

  async function refreshPrices(platforms?: string): Promise<PriceRefreshResult> {
    refreshingPrices.value = true
    try {
      const { data } = await api.post<PriceRefreshResult>('/prices/refresh', null, {
        params: platforms ? { platforms } : undefined
      })
      await Promise.all([loadValuation(), loadSummary()])
      return data
    } catch (e) {
      dashError.value = errorMessage(e)
      throw e
    } finally {
      refreshingPrices.value = false
    }
  }

  async function exportLots(format: 'csv' | 'json' | 'xlsx') {
    const { data } = await api.get<Blob>('/lots/export', {
      params: { format },
      responseType: 'blob'
    })
    downloadBlob(data, `lots.${format === 'xlsx' ? 'xlsx' : format}`)
  }

  async function importUuFullJson(file: File): Promise<UuFullJsonImportResult> {
    const body = new FormData()
    body.append('file', file)
    const { data } = await api.post<UuFullJsonImportResult>('/sync/uu/import-full-json', body)
    await Promise.all([loadLots(), loadSummary(), loadValuation()])
    return data
  }

  return {
    lots, trash, summary, pnlRows, loading, loadingSummary, loadingPnl, error, dashError,
    valuation, priceConfig, refreshingPrices, loadingValuation,
    loadLots, loadTrash, loadSummary, loadPnl, createLot, updateLot, sellLot, deleteLot, restoreLot, purgeLot,
    exportLots, downloadImportTemplate, importWorkbook, importUuFullJson,
    loadValuation, loadPriceConfig, refreshPrices
  }
})
