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

export function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { message?: string } | undefined
    return data?.message || e.message
  }
  return String(e)
}