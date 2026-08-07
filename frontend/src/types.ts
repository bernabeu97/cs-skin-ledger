export type Direction = 'BUY' | 'SELL'
export type Status = 'COMPLETED' | 'PENDING'

export interface Trade {
  id: number
  itemName: string
  platform: string
  direction: Direction
  quantity: number
  unitPrice: number
  totalAmount: number
  fee: number
  feeRate: number | null
  currency: string
  tradedAt: string
  externalTradeId: string | null
  status: Status
  note: string | null
}

export interface TradeCreateRequest {
  itemName: string
  platform: string
  direction: Direction
  quantity: number
  unitPrice: number
  fee?: number
  feeRate?: number
  currency?: string
  tradedAt: string
  externalTradeId?: string
  status?: Status
  note?: string
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