export type Direction = 'BUY' | 'SELL'
export type Status = 'COMPLETED' | 'PENDING'

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

export interface Trade {
  id: number
  itemId: number | null
  itemName: string
  itemNameZh: string | null
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
  exterior: string | null
  floatValue: number | null
}

export interface TradeCreateRequest {
  itemId?: number
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
  exterior?: string
  floatValue?: number
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