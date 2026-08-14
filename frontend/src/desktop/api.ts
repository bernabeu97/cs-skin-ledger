import { fetch } from '@tauri-apps/plugin-http'

interface CsrfView { token: string }

let baseUrl = normalizeBase(localStorage.getItem('ticker-api-base') || 'http://localhost:8080')
let csrfToken = ''

export function normalizeBase(value: string): string {
  const trimmed = value.trim().replace(/\/+$/, '')
  if (!trimmed) return trimmed
  // 兼容只填 IP/域名的输入(如 47.108.166.67、localhost:8080),自动补 http://
  const withScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`
  try {
    const url = new URL(withScheme)
    return ['http:', 'https:'].includes(url.protocol) ? url.origin : withScheme
  } catch {
    return withScheme
  }
}

export function setApiBase(value: string) {
  baseUrl = normalizeBase(value)
  localStorage.setItem('ticker-api-base', baseUrl)
  csrfToken = ''
}

export function getApiBase() {
  return baseUrl
}

export function isSecureServer(value = baseUrl): boolean {
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
    // /api/auth/csrf 返回的是经过 BREACH 防护的 XOR Token；桌面端无法读取浏览器 Cookie，
    // 因此显式声明 Token 格式，让后端使用对应的安全解码器。
    headers.set('X-CSRF-TOKEN-FORMAT', 'xor')
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

interface AuthView {
  authenticated: boolean
  username: string | null
  totpEnabled: boolean
  mfaSetupRequired: boolean
  passwordChangeRequired: boolean
}

export async function login(username: string, password: string, totpCode?: string) {
  const view = await request<AuthView>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password, totpCode: totpCode?.trim() || null })
  }, false)
  await loadCsrf()
  return view
}

export async function me() {
  const view = await request<AuthView>('/auth/me')
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
