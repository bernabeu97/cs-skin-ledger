import { ref } from 'vue'
import { defineStore } from 'pinia'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
  action?: { label: string; onClick: () => void }
}

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<ToastItem[]>([])
  const savedTheme = localStorage.getItem('skinledger-theme')
  const theme = ref<'light' | 'dark'>(savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches) ? 'dark' : 'light')
  let nextId = 1

  document.documentElement.dataset.theme = theme.value

  function toast(type: ToastType, message: string, duration = 3500, action?: ToastItem['action']) {
    const id = nextId++
    toasts.value.push({ id, type, message, action })
    if (action) duration = Math.max(duration, 6000)
    window.setTimeout(() => dismiss(id), duration)
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
    document.documentElement.dataset.theme = theme.value
    localStorage.setItem('skinledger-theme', theme.value)
  }

  return { toasts, theme, toast, dismiss, toggleTheme }
})
