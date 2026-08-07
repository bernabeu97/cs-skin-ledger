export type Direction = 'BUY' | 'SELL'
export type Status = 'COMPLETED' | 'PENDING'
export type LotStatus = 'HOLDING' | 'SOLD'

export interface Item {
  id: number
  marketHashName: string
  nameZh: string | null
  weapon: string | null
  category: string | null
  minFloat: number | null
  maxFloat: number | null
  wears: string[] | null
}

export interface Lot {
  id: number
  itemId: number
  itemName: string
  itemNameZh: string | null
  quantity: number
  exterior: string | null
  floatValue: number | null
  buyPrice: number
  buyTime: string
  buyPlatform: string
  sellPrice: number | null
  sellTime: string | null
  sellPlatform: string | null
  fee: number
  actualIncome: number | null
  profit: number | null
  status: LotStatus
  note: string | null
}

export interface LotCreateRequest {
  itemId?: number
  itemName: string
  quantity?: number
  exterior?: string
  floatValue?: number
  buyPrice: number
  buyTime: string
  buyPlatform: string
  note?: string
}

export interface LotSellRequest {
  sellPrice: number
  sellTime?: string
  sellPlatform?: string
  fee?: number
}

export interface LotSummary {
  totalBuyCost: number
  holdingCost: number
  realizedProfit: number
  lotCount: number
  holdingCount: number
  soldCount: number
}

export interface PnlRow {
  key: string
  realizedPnl: number
  tradeCount: number
}

export interface HoldingRow {
  itemName: string
  quantity: number
  avgCost: number
  realizedPnl: number
  unrealizedPnl: number | null
}

export interface ImportResult {
  created: number
  failed: number
  errors: string[]
}