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
  sellPrice?: number
  sellTime?: string
  sellPlatform?: string
  fee?: number
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

export interface UuFullJsonImportResult {
  totalRecords: number
  buyRecords: number
  sellRecords: number
  matchedSales: number
  unmatchedSales: number
  remainingHoldings: number
  correctedPriceRecords: number
  ignoredRecords: number
  holdingsImported: number
  holdingsSkippedDuplicates: number
  salesImported: number
  salesSkippedDuplicates: number
  warnings: string[]
  errors: string[]
}

// ===== 行情模块（M2/M3）=====
export interface HoldingValuation {
  lotId: number
  itemId: number
  itemName: string
  itemNameZh: string | null
  exterior: string | null
  quantity: number
  buyPrice: number
  currentPrice: number | null
  pricePlatform: 'uu' | 'steam' | 'buff' | null
  priceAt: string | null
  marketValue: number | null
  unrealizedPnl: number | null
  latestPrices: Record<string, number>
}

export interface PortfolioValuation {
  holdingCost: number
  marketValue: number
  unrealizedPnl: number
  priceAsOf: string | null
  rows: HoldingValuation[]
}

export interface PriceRefreshResult {
  startedAt: string
  finishedAt: string
  requested: number
  ok: number
  failed: number
  errors: string[]
  byPlatform: Record<string, number>
  triggeredAlerts: PriceAlert[]
}

export interface PriceConfigView {
  csqaqConfigured: boolean
  steamDirectEnabled: boolean
  youpinDirectEnabled: boolean
  messages: Record<string, string>
}

export interface PriceAlert {
  id: number
  itemId: number
  itemName: string
  itemNameZh: string | null
  exterior: string | null
  platform: string
  condition: 'gt' | 'lt'
  threshold: number
  enabled: boolean
  triggeredAt: string | null
}

// ===== 其他收支 =====
export type CostCategory = 'membership' | 'platform_fee' | 'compensation_expense' | 'compensation_income' | 'refund' | 'other'
export type CostDirection = 'expense' | 'income'

export interface OtherCost {
  id: number
  category: CostCategory
  direction: CostDirection
  amount: number
  occurredAt: string
  platform: string | null
  itemId: number | null
  itemName: string | null
  itemNameZh: string | null
  note: string | null
  sourceRef: string | null
}

export interface WatchlistItem {
  id: number
  itemId: number
  itemName: string
  itemNameZh: string | null
  exterior: string | null
  currentPrice: number | null
  priceAt: string | null
  change24h: number | null
  changePercent24h: number | null
  createdAt: string
}

export interface PricePoint {
  at: string
  value: number
}

export interface PriceHistoryView {
  itemId: number
  itemName: string
  itemNameZh: string | null
  exterior: string | null
  platform: 'uu'
  period: '24h' | '7d' | '30d' | '90d'
  points: PricePoint[]
}

export interface MarketIndexView {
  kind: 'holdings' | 'watchlist'
  period: '24h' | '7d' | '30d' | '90d'
  currentValue: number | null
  marketValue: number | null
  changePercent: number | null
  asOf: string | null
  points: PricePoint[]
}

export interface CsqaqIndex {
  id: number
  name: string
  nameKey: string
  imageUrl: string | null
  marketIndex: number | null
  changeValue: number | null
  changeRate: number | null
  open: number | null
  close: number | null
  high: number | null
  low: number | null
  updatedAt: string | null
}

export type CsqaqKlinePeriod = '1hour' | '4hour' | '1day' | '7day'

export interface CsqaqCandle {
  at: string
  open: number
  close: number
  high: number
  low: number
  volume: number
}

export interface CsqaqIndexKline {
  indexId: number
  period: CsqaqKlinePeriod
  points: CsqaqCandle[]
}

export interface CostRequest {
  category: CostCategory
  direction: CostDirection
  amount: number
  occurredAt: string
  platform?: string
  itemId?: number
  note?: string
  sourceRef?: string
}

export interface CostSummary {
  totalIncome: number
  totalExpense: number
  net: number
  byCategory: Array<{ category: CostCategory; income: number; expense: number; net: number }>
}

export const COST_CATEGORY_LABELS: Record<CostCategory, string> = {
  membership: '会员费',
  platform_fee: '平台服务费',
  compensation_expense: '赔偿支出',
  compensation_income: '赔偿收入',
  refund: '退款',
  other: '其他'
}

// ===== 平台费率设置 =====
export interface FeeSettings {
  steam: number
  uu: number
  buff: number
}

export interface CsqaqTokenStatus {
  configured: boolean
  maskedToken: string | null
  source: 'account' | 'server' | null
}
