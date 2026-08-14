<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

interface Action {
  id: string
  label: string
  hint: string
  keywords: string
  run: () => void
}

const open = ref(false)
const query = ref('')
const highlight = ref(0)
const inputEl = ref<HTMLInputElement | null>(null)
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const actions = computed<Action[]>(() => {
  const list: Action[] = [
    {
      id: 'dashboard',
      label: '仪表盘',
      hint: '概览',
      keywords: 'dashboard 概览 首页 home',
      run: () => router.push('/')
    },
    {
      id: 'trades',
      label: '饰品账本',
      hint: '账本 / 交易',
      keywords: 'trades 账本 交易 记录 lots',
      run: () => router.push('/trades')
    },
    {
      id: 'costs',
      label: '其他收支',
      hint: '会员费 / 赔偿',
      keywords: 'costs 收支 费用 会员费 赔偿',
      run: () => router.push('/costs')
    },
    {
      id: 'market',
      label: '行情盯盘',
      hint: '持仓 / 自选 / 大盘',
      keywords: 'market 行情 盯盘 价格 watchlist',
      run: () => router.push('/market')
    },
    {
      id: 'settings',
      label: '设置',
      hint: '费率 / CSQAQ Token',
      keywords: 'settings 设置 费率 token',
      run: () => router.push('/settings')
    },
    {
      id: 'security',
      label: '账号安全',
      hint: '密码 / 双重验证',
      keywords: 'security 安全 密码 验证 totp',
      run: () => router.push('/security')
    },
    ...(auth.isAdmin
      ? [{
          id: 'admin',
          label: '实例管理',
          hint: '成员 / 邀请码 / 审计',
          keywords: 'admin 管理 成员 邀请码 审计',
          run: () => router.push('/admin')
        } satisfies Action]
      : []),
    {
      id: 'new-lot',
      label: '新增买入记录',
      hint: '快捷录入',
      keywords: 'new 新增 买入 buy create',
      run: () => router.push({ path: '/trades', query: { new: '1' } })
    },
    {
      id: 'refresh-market',
      label: '刷新 UU 行情',
      hint: '拉取最新价格',
      keywords: 'refresh 刷新 行情 价格 prices',
      run: () => router.push({ path: '/market', query: { refresh: '1' } })
    },
    {
      id: 'theme',
      label: ui.theme === 'dark' ? '切换浅色主题' : '切换深色主题',
      hint: '外观',
      keywords: 'theme 主题 外观 深色 浅色 dark light',
      run: () => ui.toggleTheme()
    }
  ]
  return list
})

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return actions.value
  return actions.value.filter(a =>
    a.label.toLowerCase().includes(q) ||
    a.keywords.toLowerCase().includes(q) ||
    a.hint.toLowerCase().includes(q))
})

function onGlobalKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    toggle(true)
  } else if (e.key === 'Escape' && open.value) {
    open.value = false
  }
}

function onOpenEvent() {
  toggle(true)
}

function toggle(force?: boolean) {
  open.value = force ?? !open.value
  query.value = ''
  highlight.value = 0
  if (open.value) nextTick(() => inputEl.value?.focus())
}

function run(action: Action) {
  action.run()
  open.value = false
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    highlight.value = Math.min(highlight.value + 1, filtered.value.length - 1)
    e.preventDefault()
  } else if (e.key === 'ArrowUp') {
    highlight.value = Math.max(highlight.value - 1, 0)
    e.preventDefault()
  } else if (e.key === 'Enter' && filtered.value[highlight.value]) {
    run(filtered.value[highlight.value])
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKey)
  window.addEventListener('open-command-palette', onOpenEvent)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKey)
  window.removeEventListener('open-command-palette', onOpenEvent)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="palette-mask" @click.self="open = false">
      <section class="palette" role="dialog" aria-modal="true" aria-label="命令面板">
        <div class="palette-input-row">
          <span class="palette-icon" aria-hidden="true">⌘</span>
          <input
            ref="inputEl"
            v-model="query"
            class="palette-input"
            placeholder="输入命令或页面名称…（支持中文/英文）"
            @keydown="onKeydown"
          />
          <kbd class="palette-kbd">ESC</kbd>
        </div>
        <ul class="palette-list">
          <li v-for="(action, i) in filtered" :key="action.id" :class="{ active: i === highlight }" @mousedown.prevent="run(action)">
            <span class="palette-label">{{ action.label }}</span>
            <span class="palette-hint">{{ action.hint }}</span>
          </li>
          <li v-if="filtered.length === 0" class="palette-empty">没有匹配的命令</li>
        </ul>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.palette-mask {
  position: fixed; inset: 0; z-index: 1100;
  background: rgba(8, 11, 18, .55); backdrop-filter: blur(4px);
  display: flex; align-items: flex-start; justify-content: center;
  padding: 12vh 16px 16px;
}
.palette {
  width: min(100%, 560px); overflow: hidden;
  background: var(--surface); border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg); box-shadow: var(--shadow-lg);
}
.palette-input-row { display: flex; align-items: center; gap: 10px; padding: 14px 16px; border-bottom: 1px solid var(--border); }
.palette-icon { color: var(--accent); font-size: 18px; }
.palette-input {
  flex: 1; border: 0; outline: none; background: transparent;
  color: var(--text); font-size: 15px; font-family: inherit;
}
.palette-input::placeholder { color: var(--text-muted); }
.palette-kbd { font-family: var(--font-mono); font-size: 10px; color: var(--text-muted); border: 1px solid var(--border); border-bottom-width: 2px; border-radius: 4px; padding: 1px 5px; }
.palette-list { list-style: none; margin: 0; padding: 6px; max-height: 380px; overflow: auto; }
.palette-list li { display: flex; align-items: center; gap: 10px; padding: 9px 12px; border-radius: var(--radius-sm); cursor: pointer; }
.palette-list li.active { background: var(--accent-soft); }
.palette-label { font-size: 13px; font-weight: 550; }
.palette-hint { margin-left: auto; font-size: 11px; color: var(--text-muted); }
.palette-empty { justify-content: center; color: var(--text-muted); cursor: default; }
</style>
