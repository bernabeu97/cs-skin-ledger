<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useAlertsStore } from '../stores/alerts'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

const alerts = useAlertsStore()
const auth = useAuthStore()
const ui = useUiStore()

const SEEN_KEY = 'skinledger-alert-last-seen'
let timer: number | undefined
let titleTimer: number | undefined

function lastSeen(): string {
  return window.localStorage.getItem(SEEN_KEY) ?? ''
}

function flashTitle(count: number) {
  const original = document.title
  document.title = `⚠ ${count} 条价格提醒已触发`
  window.clearTimeout(titleTimer)
  titleTimer = window.setTimeout(() => {
    document.title = original
  }, 8000)
}

async function poll() {
  if (!auth.authenticated) return
  try {
    await alerts.loadAlerts()
  } catch {
    return
  }
  const seen = lastSeen()
  const triggered = alerts.alerts.filter(a => a.triggeredAt != null && a.triggeredAt > seen)
  if (triggered.length === 0) return
  const times = triggered.map(a => a.triggeredAt ?? '').sort()
  const latest = times.length ? times[times.length - 1] : ''
  window.localStorage.setItem(SEEN_KEY, latest)
  flashTitle(triggered.length)
  if (document.visibilityState === 'visible') {
    for (const alert of triggered.slice(0, 3)) {
      ui.toast('info',
        `${alert.itemNameZh ?? alert.itemName}${alert.exterior ? `（${alert.exterior}）` : ''} 已达提醒价 ¥${alert.threshold}`, 8000)
    }
  }
}

function onVisible() {
  if (document.visibilityState === 'visible') poll()
}

onMounted(() => {
  timer = window.setInterval(poll, 60_000)
  document.addEventListener('visibilitychange', onVisible)
})
watch(() => auth.authenticated, (v) => {
  if (v) poll()
})
onBeforeUnmount(() => {
  window.clearInterval(timer)
  window.clearTimeout(titleTimer)
  document.removeEventListener('visibilitychange', onVisible)
})
</script>

<template>
  <span class="visually-hidden" aria-hidden="true"></span>
</template>

<style scoped>
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
</style>
