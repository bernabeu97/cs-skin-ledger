import axios from 'axios'

export const api = axios.create({ baseURL: '/api' })

export interface TradeQuery {
  platform?: string
  direction?: string
  from?: string
  to?: string
  q?: string
  category?: string
}