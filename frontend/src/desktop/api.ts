import { fetch } from '@tauri-apps/plugin-http'

interface CsrfView { token: string }

let baseUrl = normalizeBase(localStorage.getItem('ticker-api-base') || 'http://localhost:8080')
let csrfToken = ''

export function normalizeBase(value: string): string {
  return value.trim().replace(/\/+$/, '')
}

export function setApiBase(value: string) {
  baseUrl = normalizeBase(value)
  localStorage.setItem('ticker-api-base', baseUrl)
  csrfToken = ''
}

export function getApiBase() {
  return baseUrl
}

export function canPersistCredentials(value = baseUrl): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'https:' || ['localhost', '127.0.0.1', '::1'].includes(url.hostname)
  } catch {
    return false
  }
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(data?.message || `请求失败（${response.status}）`)
  }
  return data as T
}

async function request<T>(path: string, init: RequestInit = {}, retryCsrf = true): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && csrfToken) {
    headers.set('X-XSRF-TOKEN', csrfToken)
  }
  const response = await fetch(`${baseUrl}/api${path}`, { ...init, headers, credentials: 'include' })
  if (response.status === 403 && retryCsrf && !path.startsWith('/auth/')) {
    await loadCsrf()
    return request<T>(path, init, false)
  }
  return parseResponse<T>(response)
}

export async function loadCsrf() {
  const data = await request<CsrfView>('/auth/csrf', {}, false)
  csrfToken = data.token
}

export async function login(username: string, password: string) {
  const view = await request<{ authenticated: boolean; username: string }>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  }, false)
  await loadCsrf()
  return view
}

export async function me() {
  const view = await request<{ authenticated: boolean; username: string | null }>('/auth/me')
  if (view.authenticated) await loadCsrf()
  return view
}

export const desktopApi = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, {
    method: 'POST', body: body === undefined ? undefined : JSON.stringify(body)
  }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' })
}
