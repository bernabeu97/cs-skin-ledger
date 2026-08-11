<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { invoke } from '@tauri-apps/api/core'
import { LogicalSize } from '@tauri-apps/api/dpi'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { disable, enable, isEnabled } from '@tauri-apps/plugin-autostart'
import {
  isPermissionGranted,
  onAction,
  registerActionTypes,
  requestPermission,
  sendNotification
} from '@tauri-apps/plugin-notification'
import MarketLineChart from '../components/MarketLineChart.vue'
import { desktopApi, canPersistCredentials, getApiBase, login, me, setApiBase } from './api'
import type {
  Item,
  MarketIndexView,
  PortfolioValuation,
  PriceAlert,
  PriceHistoryView,
  PriceRefreshResult,
  WatchlistItem
} from '../types'
import { formatDateTime, formatMoney, formatSignedMoney } from '../utils/format'

type Tab = 'holdings' | 'watchlist' | 'market'
type Period = '24h' | '7d' | '30d' | '90d'

interface SavedCredentials { username: string; password: string }
interface CachePayload {
  savedAt: string
  valuation: PortfolioValuation | null
  watchlist: WatchlistItem[]
  holdingIndex: MarketIndexView | null
  watchIndex: MarketIndexView | null
  alerts: PriceAlert[]
}

const auth = reactive({ authenticated: false, username: '' })
const loginForm = reactive({
  server: getApiBase(), username: '', password: '', remember: true
})
const settings = reactive({
  refreshMinutes: Number(localStorage.getItem('ticker-refresh-minutes') || 5),
  quietStart: localStorage.getItem('ticker-quiet-start') || '23:00',
  quietEnd: localStorage.getItem('ticker-quiet-end') || '08:00',
  alwaysOnTop: localStorage.getItem('ticker-always-on-top') !== '0',
  autoStart: false
})

const compact = ref(localStorage.getItem('ticker-expanded') !== '1')
const tab = ref<Tab>('holdings')
const period = ref<Period>('24h')
const loading = ref(false)
const refreshing = ref(false)
const loginBusy = ref(false)
const loginError = ref('')
const error = ref('')
const showSettings = ref(false)
const stale = ref(false)
const lastUpdated = ref<string | null>(null)
const valuation = ref<PortfolioValuation | null>(null)
const watchlist = ref<WatchlistItem[]>([])
const holdingIndex = ref<MarketIndexView | null>(null)
const watchIndex = ref<MarketIndexView | null>(null)
const selectedWatch = ref<WatchlistItem | null>(null)
const itemHistory = ref<PriceHistoryView | null>(null)
const alerts = ref<PriceAlert[]>([])
const searchText = ref('')
const searchResults = ref<Item[]>([])
const selectedItem = ref<Item | null>(null)
const selectedExterior = ref('')
const alertCondition = ref<'gt' | 'lt'>('gt')
const alertThreshold = ref<number | null>(null)
let pollTimer: number | undefined
let actionListener: { unregister: () => void } | undefined

const periods: Array<{ value: Period; label: string }> = [
  { value: '24h', label: '24H' }, { value: '7d', label: '7D' },
  { value: '30d', label: '30D' }, { value: '90d', label: '90D' }
]
const serverIsSafe = computed(() => canPersistCredentials(loginForm.server))
const currentIndex = computed(() => tab.value === 'watchlist' ? watchIndex.value : holdingIndex.value)
const topMovers = computed(() => [...watchlist.value]
  .filter(item => item.changePercent24h != null)
  .sort((a, b) => Math.abs(b.changePercent24h ?? 0) - Math.abs(a.changePercent24h ?? 0))
  .slice(0, 3))
const connectionLabel = computed(() => stale.value ? '缓存数据' : refreshing.value ? '刷新中' : '行情在线')

function cachePayload(): CachePayload {
  return {
    savedAt: new Date().toISOString(), valuation: valuation.value, watchlist: watchlist.value,
    holdingIndex: holdingIndex.value, watchIndex: watchIndex.value, alerts: alerts.value
  }
}

function restoreCache(): boolean {
  const raw = localStorage.getItem('ticker-cache')
  if (!raw) return false
  try {
    const cache = JSON.parse(raw) as CachePayload
    valuation.value = cache.valuation
    watchlist.value = cache.watchlist
    holdingIndex.value = cache.holdingIndex
    watchIndex.value = cache.watchIndex
    alerts.value = cache.alerts ?? []
    lastUpdated.value = cache.savedAt
    stale.value = true
    selectedWatch.value = watchlist.value[0] ?? null
    return true
  } catch {
    return false
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [nextValuation, nextWatchlist, nextHoldingIndex, nextWatchIndex, nextAlerts] = await Promise.all([
      desktopApi.get<PortfolioValuation>('/prices/valuation'),
      desktopApi.get<WatchlistItem[]>('/watchlist'),
      desktopApi.get<MarketIndexView>(`/prices/index?kind=holdings&period=${period.value}`),
      desktopApi.get<MarketIndexView>(`/prices/index?kind=watchlist&period=${period.value}`),
      desktopApi.get<PriceAlert[]>('/alerts')
    ])
    valuation.value = nextValuation
    watchlist.value = nextWatchlist
    holdingIndex.value = nextHoldingIndex
    watchIndex.value = nextWatchIndex
    alerts.value = nextAlerts
    if (!selectedWatch.value || !nextWatchlist.some(item => item.id === selectedWatch.value?.id)) {
      selectedWatch.value = nextWatchlist[0] ?? null
    }
    lastUpdated.value = nextValuation.priceAsOf || new Date().toISOString()
    stale.value = false
    localStorage.setItem('ticker-cache', JSON.stringify(cachePayload()))
    if (!compact.value && tab.value === 'watchlist' && selectedWatch.value) await loadItemHistory()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    if (!restoreCache()) stale.value = true
  } finally {
    loading.value = false
  }
}

async function refreshPrices() {
  if (refreshing.value || !auth.authenticated) return
  refreshing.value = true
  error.value = ''
  try {
    const result = await desktopApi.post<PriceRefreshResult>('/prices/refresh?platforms=uu')
    await loadData()
    for (const alert of result.triggeredAlerts ?? []) await notifyAlert(alert)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : String(cause)
    stale.value = true
  } finally {
    refreshing.value = false
  }
}

async function submitLogin() {
  if (loginBusy.value) return
  loginBusy.value = true
  loginError.value = ''
  try {
    setApiBase(loginForm.server)
    const view = await login(loginForm.username.trim(), loginForm.password)
    auth.authenticated = view.authenticated
    auth.username = view.username
    if (loginForm.remember && serverIsSafe.value) {
      await invoke('save_credentials', { username: loginForm.username.trim(), password: loginForm.password })
    } else {
      await invoke('delete_credentials')
    }
    loginForm.password = ''
    await loadData()
    startPolling()
  } catch (cause) {
    loginError.value = cause instanceof Error ? cause.message : String(cause)
  } finally {
    loginBusy.value = false
  }
}

async function tryAutoLogin() {
  try {
    const current = await me()
    if (current.authenticated) {
      auth.authenticated = true
      auth.username = current.username || ''
      await loadData()
      startPolling()
      return
    }
  } catch { /* 继续尝试安全保存的账号 */ }
  if (!canPersistCredentials()) return
  try {
    const saved = await invoke<SavedCredentials | null>('load_credentials')
    if (!saved) return
    loginForm.username = saved.username
    loginForm.password = saved.password
    loginForm.remember = true
    await submitLogin()
  } catch { /* 保持在登录页 */ }
}

async function logout() {
  await invoke('delete_credentials')
  auth.authenticated = false
  auth.username = ''
  window.clearInterval(pollTimer)
}

function startPolling() {
  window.clearInterval(pollTimer)
  pollTimer = window.setInterval(refreshPrices, settings.refreshMinutes * 60_000)
}

async function toggleExpanded() {
  compact.value = !compact.value
  localStorage.setItem('ticker-expanded', compact.value ? '0' : '1')
  await getCurrentWindow().setSize(new LogicalSize(compact.value ? 420 : 1020, compact.value ? 620 : 760))
  if (!compact.value) await loadData()
}

async function saveSettings() {
  localStorage.setItem('ticker-refresh-minutes', String(settings.refreshMinutes))
  localStorage.setItem('ticker-quiet-start', settings.quietStart)
  localStorage.setItem('ticker-quiet-end', settings.quietEnd)
  localStorage.setItem('ticker-always-on-top', settings.alwaysOnTop ? '1' : '0')
  await getCurrentWindow().setAlwaysOnTop(settings.alwaysOnTop)
  if (settings.autoStart) await enable(); else await disable()
  startPolling()
  showSettings.value = false
}

function inQuietHours() {
  const now = new Date()
  const value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  return settings.quietStart <= settings.quietEnd
    ? value >= settings.quietStart && value < settings.quietEnd
    : value >= settings.quietStart || value < settings.quietEnd
}

async function notifyAlert(alert: PriceAlert) {
  if (inQuietHours()) return
  let granted = await isPermissionGranted()
  if (!granted) granted = (await requestPermission()) === 'granted'
  if (!granted) return
  sendNotification({
    title: 'UU 价格提醒',
    body: `${alert.itemNameZh ?? alert.itemName}${alert.exterior ? ` · ${alert.exterior}` : ''} ${alert.condition === 'gt' ? '高于' : '低于'} ${formatMoney(alert.threshold)}`,
    actionTypeId: 'price-alert',
    extra: { itemId: alert.itemId },
    silent: true,
    autoCancel: true
  })
}

async function setupNotifications() {
  try {
    await registerActionTypes([{ id: 'price-alert', actions: [{ id: 'open', title: '查看饰品', foreground: true }] }])
    actionListener = await onAction(async event => {
      const itemId = Number(event.extra?.itemId)
      await getCurrentWindow().show()
      await getCurrentWindow().setFocus()
      if (itemId) {
        await toggleExpandedIfNeeded()
        tab.value = 'watchlist'
        selectedWatch.value = watchlist.value.find(item => item.itemId === itemId) ?? null
        if (selectedWatch.value) await loadItemHistory()
      }
    })
  } catch { /* Windows 通知不可用时不影响盯盘 */ }
}

async function toggleExpandedIfNeeded() {
  if (compact.value) await toggleExpanded()
}

async function changePeriod(next: Period) {
  period.value = next
  await loadData()
}

async function selectWatch(entry: WatchlistItem) {
  selectedWatch.value = entry
  await loadItemHistory()
}

async function loadItemHistory() {
  if (!selectedWatch.value) {
    itemHistory.value = null
    return
  }
  const entry = selectedWatch.value
  itemHistory.value = await desktopApi.get<PriceHistoryView>(
    `/prices/history?itemId=${entry.itemId}&exterior=${encodeURIComponent(entry.exterior || '')}&period=${period.value}`
  )
}

async function searchItems() {
  if (!searchText.value.trim()) {
    searchResults.value = []
    return
  }
  searchResults.value = await desktopApi.get<Item[]>(`/items/search?q=${encodeURIComponent(searchText.value.trim())}&limit=20`)
}

function chooseItem(item: Item) {
  selectedItem.value = item
  selectedExterior.value = ''
  searchText.value = item.nameZh ?? item.marketHashName
  searchResults.value = []
}

async function addWatch() {
  if (!selectedItem.value) return
  await desktopApi.post('/watchlist', { itemId: selectedItem.value.id, exterior: selectedExterior.value || null })
  selectedItem.value = null
  selectedExterior.value = ''
  searchText.value = ''
  await refreshPrices()
}

async function removeWatch(entry: WatchlistItem) {
  await desktopApi.delete(`/watchlist/${entry.id}`)
  await loadData()
}

async function addAlert() {
  if (!selectedWatch.value || !alertThreshold.value || alertThreshold.value <= 0) return
  await desktopApi.post('/alerts', {
    itemId: selectedWatch.value.itemId,
    exterior: selectedWatch.value.exterior,
    platform: 'uu',
    condition: alertCondition.value,
    threshold: alertThreshold.value
  })
  alertThreshold.value = null
  await loadData()
}

async function deleteAlert(id: number) {
  await desktopApi.delete(`/alerts/${id}`)
  await loadData()
}

watch(() => settings.refreshMinutes, value => {
  if (![1, 5, 10, 30].includes(value)) settings.refreshMinutes = 5
})

onMounted(async () => {
  settings.autoStart = await isEnabled().catch(() => false)
  await getCurrentWindow().setAlwaysOnTop(settings.alwaysOnTop)
  await setupNotifications()
  restoreCache()
  await tryAutoLogin()
})
onBeforeUnmount(() => {
  window.clearInterval(pollTimer)
  actionListener?.unregister()
})
</script>

<template>
  <div class="desktop-root" :class="{ compact }">
    <div v-if="!auth.authenticated" class="login-shell">
      <div class="login-brand"><span>CS</span><b>饰品UU盯盘</b></div>
      <form class="login-card" @submit.prevent="submitLogin">
        <div><h1>连接记账服务</h1><p>登录后同步持仓、自选和 UU 行情。</p></div>
        <label><span>服务地址</span><input v-model.trim="loginForm.server" class="input" type="url" required /></label>
        <div v-if="!serverIsSafe" class="security-warning">当前是公网 HTTP 地址：可以手动登录，但不会保存密码或自动登录。</div>
        <label><span>账号</span><input v-model.trim="loginForm.username" class="input" autocomplete="username" required /></label>
        <label><span>密码</span><input v-model="loginForm.password" class="input" type="password" autocomplete="current-password" required /></label>
        <label class="remember"><input v-model="loginForm.remember" type="checkbox" :disabled="!serverIsSafe" />使用 Windows 凭据管理器安全记住账号</label>
        <p v-if="loginError" class="login-error">{{ loginError }}</p>
        <button type="submit" class="btn btn-primary login-button" :disabled="loginBusy">{{ loginBusy ? '连接中…' : '登录' }}</button>
      </form>
    </div>

    <template v-else>
      <header class="ticker-head">
        <div class="brand"><span class="brand-mark">CS</span><b>UU盯盘</b></div>
        <div class="connection" :class="{ stale }"><span></span>{{ connectionLabel }}</div>
        <div class="head-actions">
          <button type="button" title="设置" @click="showSettings = true">设置</button>
          <button type="button" :title="compact ? '展开完整行情' : '收起小窗'" @click="toggleExpanded">{{ compact ? '展开' : '收起' }}</button>
        </div>
      </header>

      <main v-if="compact" class="compact-body" @dblclick="toggleExpanded">
        <section class="hero-metric">
          <span>当前持仓市值</span>
          <strong class="num">{{ valuation ? formatMoney(valuation.marketValue) : '-' }}</strong>
          <div class="hero-meta">
            <span :class="(holdingIndex?.changePercent ?? 0) >= 0 ? 'up' : 'down'">24H {{ holdingIndex?.changePercent == null ? '-' : `${holdingIndex.changePercent >= 0 ? '+' : ''}${holdingIndex.changePercent.toFixed(2)}%` }}</span>
            <span>浮盈 {{ valuation ? formatSignedMoney(valuation.unrealizedPnl) : '-' }}</span>
          </div>
        </section>

        <section class="movers">
          <div class="compact-title"><b>关注波动</b><span>24小时</span></div>
          <button v-for="item in topMovers" :key="item.id" type="button" class="mover-row" @click="toggleExpandedIfNeeded().then(() => selectWatch(item)); tab = 'watchlist'">
            <span><b>{{ item.itemNameZh ?? item.itemName }}</b><small>{{ item.exterior ?? '无磨损' }}</small></span>
            <span class="num"><b>{{ item.currentPrice == null ? '-' : formatMoney(item.currentPrice) }}</b><small :class="(item.changePercent24h ?? 0) >= 0 ? 'up' : 'down'">{{ item.changePercent24h == null ? '-' : `${item.changePercent24h >= 0 ? '+' : ''}${item.changePercent24h.toFixed(2)}%` }}</small></span>
          </button>
          <div v-if="topMovers.length === 0" class="compact-empty">添加自选并刷新两次行情后显示24小时波动。</div>
        </section>

        <footer class="compact-footer">
          <span>{{ stale ? '数据已过期 · ' : '' }}{{ lastUpdated ? formatDateTime(lastUpdated) : '尚未同步' }}</span>
          <button type="button" :disabled="refreshing" @click="refreshPrices">{{ refreshing ? '同步中…' : '立即刷新' }}</button>
        </footer>
      </main>

      <main v-else class="expanded-body">
        <div class="expanded-toolbar">
          <nav><button :class="{ active: tab === 'holdings' }" @click="tab = 'holdings'">持仓</button><button :class="{ active: tab === 'watchlist' }" @click="tab = 'watchlist'; loadItemHistory()">自选</button><button :class="{ active: tab === 'market' }" @click="tab = 'market'">大盘 <small>待接入</small></button></nav>
          <div class="periods"><button v-for="item in periods" :key="item.value" :class="{ active: period === item.value }" @click="changePeriod(item.value)">{{ item.label }}</button></div>
          <button type="button" class="btn btn-primary btn-sm" :disabled="refreshing" @click="refreshPrices">{{ refreshing ? '刷新中…' : '刷新UU行情' }}</button>
        </div>
        <div v-if="error" class="desktop-error">{{ error }} <button @click="loadData">重试</button></div>

        <template v-if="tab === 'holdings'">
          <div class="desktop-metrics"><div><span>当前市值</span><b class="num">{{ valuation ? formatMoney(valuation.marketValue) : '-' }}</b></div><div><span>浮动盈亏</span><b class="num" :class="(valuation?.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">{{ valuation ? formatSignedMoney(valuation.unrealizedPnl) : '-' }}</b></div><div><span>{{ period }}涨跌</span><b class="num" :class="(holdingIndex?.changePercent ?? 0) >= 0 ? 'up' : 'down'">{{ holdingIndex?.changePercent == null ? '-' : `${holdingIndex.changePercent >= 0 ? '+' : ''}${holdingIndex.changePercent.toFixed(2)}%` }}</b></div></div>
          <MarketLineChart title="持仓指数" subtitle="按市值加权 · 成分变化链式衔接 · UU价" :points="holdingIndex?.points ?? []" :loading="loading" value-type="index" />
          <div class="desktop-list"><div v-for="row in valuation?.rows ?? []" :key="row.lotId" class="holding-row"><span><b>{{ row.itemNameZh ?? row.itemName }}</b><small>{{ row.exterior ?? '无磨损' }} · {{ row.quantity }}件</small></span><span class="num"><b>{{ row.currentPrice == null ? '-' : formatMoney(row.currentPrice) }}</b><small :class="(row.unrealizedPnl ?? 0) >= 0 ? 'up' : 'down'">{{ row.unrealizedPnl == null ? '-' : formatSignedMoney(row.unrealizedPnl) }}</small></span></div></div>
        </template>

        <template v-else-if="tab === 'watchlist'">
          <div class="desktop-watch-layout">
            <aside class="watch-sidebar">
              <form class="desktop-search" @submit.prevent="searchItems"><input v-model="searchText" class="input" placeholder="搜索中文饰品" /><button class="btn btn-sm">搜索</button></form>
              <div v-if="searchResults.length" class="search-results"><button v-for="item in searchResults" :key="item.id" @click="chooseItem(item)">{{ item.nameZh ?? item.marketHashName }}</button></div>
              <div v-if="selectedItem" class="add-selection"><select v-model="selectedExterior" class="select"><option value="">无磨损</option><option v-for="wear in selectedItem.wears ?? []" :key="wear">{{ wear }}</option></select><button class="btn btn-primary btn-sm" @click="addWatch">加入</button></div>
              <button v-for="entry in watchlist" :key="entry.id" class="desktop-watch-row" :class="{ active: selectedWatch?.id === entry.id }" @click="selectWatch(entry)"><span><b>{{ entry.itemNameZh ?? entry.itemName }}</b><small>{{ entry.exterior ?? '无磨损' }}</small></span><span class="num"><b>{{ entry.currentPrice == null ? '-' : formatMoney(entry.currentPrice) }}</b><small :class="(entry.changePercent24h ?? 0) >= 0 ? 'up' : 'down'">{{ entry.changePercent24h == null ? '-' : `${entry.changePercent24h >= 0 ? '+' : ''}${entry.changePercent24h.toFixed(2)}%` }}</small></span></button>
            </aside>
            <section class="watch-content">
              <MarketLineChart :title="selectedWatch ? (selectedWatch.itemNameZh ?? selectedWatch.itemName) : '单品UU价格'" :subtitle="selectedWatch ? `${selectedWatch.exterior ?? '无磨损'} · ${period}` : '从左侧选择饰品'" :points="itemHistory?.points ?? []" :loading="loading" value-type="price" />
              <div v-if="selectedWatch" class="alert-editor"><select v-model="alertCondition" class="select"><option value="gt">价格高于</option><option value="lt">价格低于</option></select><input v-model.number="alertThreshold" class="input num" type="number" min="0.01" step="0.01" placeholder="提醒价" /><button class="btn btn-primary" @click="addAlert">添加提醒</button><button class="btn btn-ghost" @click="removeWatch(selectedWatch)">移出自选</button></div>
              <div class="alert-list"><span v-for="alert in alerts.filter(a => !selectedWatch || a.itemId === selectedWatch.itemId)" :key="alert.id" class="alert-chip">{{ alert.exterior ?? '无磨损' }} · {{ alert.condition === 'gt' ? '高于' : '低于' }} {{ formatMoney(alert.threshold) }}<button @click="deleteAlert(alert.id)">×</button></span></div>
            </section>
          </div>
        </template>

        <section v-else class="desktop-market-empty"><span>MARKET INDEX</span><h2>全市场大盘数据源未接入</h2><p>不使用虚假或未经验证的数据。持仓指数与自选指数可正常使用。</p></section>
      </main>

      <div v-if="showSettings" class="settings-mask" @click.self="showSettings = false"><section class="settings-panel"><header><div><b>盯盘设置</b><small>{{ auth.username }}</small></div><button @click="showSettings = false">×</button></header><label><span>服务地址</span><input v-model="loginForm.server" class="input" disabled /><small>修改地址后请退出并重新登录。</small></label><label><span>刷新周期</span><select v-model.number="settings.refreshMinutes" class="select"><option :value="1">1分钟</option><option :value="5">5分钟（默认）</option><option :value="10">10分钟</option><option :value="30">30分钟</option></select></label><div class="quiet-grid"><label><span>免打扰开始</span><input v-model="settings.quietStart" class="input" type="time" /></label><label><span>免打扰结束</span><input v-model="settings.quietEnd" class="input" type="time" /></label></div><label class="switch-row"><input v-model="settings.alwaysOnTop" type="checkbox" /><span>窗口始终置顶</span></label><label class="switch-row"><input v-model="settings.autoStart" type="checkbox" /><span>开机自动启动</span></label><footer><button class="btn btn-ghost" @click="logout">退出账号</button><button class="btn btn-primary" @click="saveSettings">保存设置</button></footer></section></div>
    </template>
  </div>
</template>

<style scoped>
:global(body) { overflow: hidden; background: #f4f6f9; }
.desktop-root { min-height: 100vh; color: #17181c; background: #f4f6f9; }
button { font-family: inherit; }
.login-shell { min-height: 100vh; display: grid; place-items: start center; padding: 88px 24px 24px; overflow: auto; background: #111318; }
.login-brand { position: fixed; top: 22px; left: 24px; display: flex; align-items: center; gap: 8px; color: #fff; }
.login-brand span, .brand-mark { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 7px; background: #3972dc; color: #fff; font: 700 11px var(--font-mono); }
.login-card { width: min(380px, 100%); padding: 22px; border-radius: 12px; background: #fff; display: flex; flex-direction: column; gap: 13px; box-shadow: 0 18px 60px rgba(0,0,0,.3); }
.login-card h1 { margin: 0 0 3px; }.login-card p { margin: 0; color: var(--text-muted); font-size: 12px; }.login-card label { display: flex; flex-direction: column; gap: 4px; }.login-card label > span { color: var(--text-secondary); font-size: 11px; font-weight: 600; }
.security-warning { padding: 8px 10px; border: 1px solid #f5c77b; border-radius: 6px; background: #fff7e6; color: #7a4f01; font-size: 11px; }.login-card .remember { flex-direction: row; align-items: center; color: var(--text-secondary); font-size: 11px; }.login-error { color: var(--danger) !important; }.login-button { width: 100%; }
.ticker-head { height: 48px; display: flex; align-items: center; gap: 12px; padding: 0 13px; background: #111318; color: #fff; user-select: none; }
.brand { display: flex; align-items: center; gap: 7px; font-size: 12px; }.brand-mark { width: 25px; height: 25px; border-radius: 6px; font-size: 10px; }.connection { display: flex; align-items: center; gap: 5px; margin-left: auto; color: #b9c0cc; font-size: 10px; }.connection > span { width: 6px; height: 6px; border-radius: 50%; background: #35b46f; }.connection.stale > span { background: #e4a11b; }.head-actions { display: flex; gap: 2px; }.head-actions button { border: 0; border-radius: 5px; padding: 5px 7px; background: transparent; color: #c5cad3; font-size: 10px; cursor: pointer; }.head-actions button:hover { background: rgba(255,255,255,.09); color: #fff; }
.compact-body { height: calc(100vh - 48px); display: flex; flex-direction: column; padding: 16px; }
.hero-metric { padding: 18px; border: 1px solid #d8e2f5; border-radius: 10px; background: linear-gradient(145deg, #fff 0%, #edf4ff 100%); }.hero-metric > span { color: var(--text-secondary); font-size: 11px; }.hero-metric > strong { display: block; margin: 5px 0; font-size: 31px; letter-spacing: -.03em; }.hero-meta { display: flex; justify-content: space-between; color: var(--text-secondary); font-size: 11px; }
.movers { margin-top: 14px; background: #fff; border: 1px solid var(--border); border-radius: 9px; overflow: hidden; }.compact-title { display: flex; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid var(--border); font-size: 11px; }.compact-title span { color: var(--text-muted); }.mover-row { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr) 100px; gap: 8px; padding: 10px 12px; border: 0; border-bottom: 1px solid var(--border); background: transparent; text-align: left; cursor: pointer; }.mover-row:last-child { border-bottom: 0; }.mover-row:hover { background: #f8fafc; }.mover-row > span { display: flex; flex-direction: column; min-width: 0; }.mover-row b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }.mover-row small { color: var(--text-muted); font-size: 9px; }.mover-row > .num { text-align: right; }.compact-empty { padding: 24px 12px; color: var(--text-muted); text-align: center; font-size: 10px; }
.compact-footer { display: flex; justify-content: space-between; align-items: center; margin-top: auto; padding-top: 12px; color: var(--text-muted); font-size: 9px; }.compact-footer button { border: 0; background: transparent; color: var(--accent); font-size: 10px; cursor: pointer; }
.expanded-body { height: calc(100vh - 48px); padding: 14px 18px 24px; overflow: auto; }.expanded-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }.expanded-toolbar nav { display: flex; gap: 2px; }.expanded-toolbar nav button, .periods button { border: 0; background: transparent; padding: 6px 10px; color: var(--text-secondary); font-size: 11px; cursor: pointer; border-radius: 6px; }.expanded-toolbar nav button.active, .periods button.active { color: var(--accent); background: var(--accent-soft); font-weight: 600; }.expanded-toolbar nav small { color: var(--text-muted); font-size: 8px; }.periods { display: flex; margin-left: auto; }.desktop-error { margin-bottom: 10px; padding: 8px 10px; background: var(--danger-soft); color: var(--danger); border-radius: 6px; font-size: 11px; }.desktop-error button { border: 0; background: transparent; color: inherit; text-decoration: underline; cursor: pointer; }
.desktop-metrics { display: grid; grid-template-columns: repeat(3, 1fr); margin-bottom: 12px; border: 1px solid var(--border); border-radius: 8px; background: #fff; }.desktop-metrics > div { padding: 11px 14px; border-right: 1px solid var(--border); }.desktop-metrics > div:last-child { border-right: 0; }.desktop-metrics span { display: block; color: var(--text-muted); font-size: 9px; }.desktop-metrics b { font-size: 18px; }.desktop-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 7px; margin-top: 12px; }.holding-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; padding: 9px 11px; border: 1px solid var(--border); border-radius: 7px; background: #fff; }.holding-row > span { display: flex; flex-direction: column; min-width: 0; }.holding-row > .num { text-align: right; }.holding-row b { font-size: 10px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }.holding-row small { color: var(--text-muted); font-size: 8px; }
.desktop-watch-layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 12px; }.watch-sidebar { min-height: 640px; border: 1px solid var(--border); border-radius: 8px; background: #fff; overflow: hidden; }.desktop-search { display: grid; grid-template-columns: 1fr auto; gap: 5px; padding: 9px; border-bottom: 1px solid var(--border); }.search-results { max-height: 150px; overflow: auto; border-bottom: 1px solid var(--border); }.search-results button { width: 100%; padding: 7px 9px; border: 0; border-bottom: 1px solid var(--border); background: #fff; text-align: left; font-size: 9px; cursor: pointer; }.search-results button:hover { background: var(--accent-soft); }.add-selection { display: grid; grid-template-columns: 1fr auto; gap: 5px; padding: 8px; border-bottom: 1px solid var(--border); }.desktop-watch-row { width: 100%; display: grid; grid-template-columns: minmax(0, 1fr) 85px; gap: 8px; padding: 9px 10px; border: 0; border-bottom: 1px solid var(--border); background: transparent; text-align: left; cursor: pointer; }.desktop-watch-row.active { background: var(--accent-soft); box-shadow: inset 3px 0 var(--accent); }.desktop-watch-row span { display: flex; flex-direction: column; min-width: 0; }.desktop-watch-row > .num { text-align: right; }.desktop-watch-row b { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 9px; }.desktop-watch-row small { color: var(--text-muted); font-size: 8px; }.watch-content { min-width: 0; }.alert-editor { display: grid; grid-template-columns: 120px 120px auto auto; gap: 7px; margin-top: 10px; }.alert-list { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 8px; }.alert-chip { display: inline-flex; gap: 5px; align-items: center; padding: 4px 7px; border-radius: 999px; background: #eef0f3; color: var(--text-secondary); font-size: 8px; }.alert-chip button { border: 0; background: transparent; color: var(--text-muted); cursor: pointer; }
.desktop-market-empty { min-height: 480px; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; border: 1px solid var(--border); border-radius: 9px; background: #fff; }.desktop-market-empty > span { font: 9px var(--font-mono); letter-spacing: .16em; color: var(--text-muted); }.desktop-market-empty h2 { margin: 12px 0 5px; }.desktop-market-empty p { margin: 0; color: var(--text-secondary); font-size: 11px; }
.settings-mask { position: fixed; inset: 0; z-index: 100; display: flex; justify-content: flex-end; background: rgba(17,19,24,.42); }.settings-panel { width: min(360px, 90vw); height: 100%; padding: 18px; background: #fff; box-shadow: -12px 0 36px rgba(0,0,0,.15); display: flex; flex-direction: column; gap: 14px; }.settings-panel header { display: flex; justify-content: space-between; align-items: flex-start; padding-bottom: 12px; border-bottom: 1px solid var(--border); }.settings-panel header div { display: flex; flex-direction: column; }.settings-panel header small, .settings-panel label small { color: var(--text-muted); font-size: 9px; }.settings-panel header button { border: 0; background: transparent; font-size: 20px; cursor: pointer; }.settings-panel > label, .quiet-grid label { display: flex; flex-direction: column; gap: 4px; }.settings-panel label > span { color: var(--text-secondary); font-size: 10px; font-weight: 600; }.quiet-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }.settings-panel .switch-row { flex-direction: row; align-items: center; }.settings-panel footer { display: flex; justify-content: space-between; margin-top: auto; padding-top: 12px; border-top: 1px solid var(--border); }
.up { color: var(--success) !important; }.down { color: var(--danger) !important; }
@media (max-width: 760px) { .desktop-list { grid-template-columns: 1fr; }.desktop-watch-layout { grid-template-columns: 1fr; }.watch-sidebar { min-height: 0; }.alert-editor { grid-template-columns: 1fr 1fr; } }
</style>
