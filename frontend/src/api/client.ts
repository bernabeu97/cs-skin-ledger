import axios from 'axios'
import type { Item } from '../types'

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

export async function searchItems(q: string, limit = 50): Promise<Item[]> {
  const { data } = await api.get<Item[]>('/items/search', { params: { q, limit } })
  return data
}

export async function getItem(id: number): Promise<Item> {
  const { data } = await api.get<Item>(`/items/${id}`)
  return data
}