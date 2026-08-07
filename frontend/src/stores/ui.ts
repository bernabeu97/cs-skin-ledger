import { ref } from 'vue'
import { defineStore } from 'pinia'

export type ToastType = 'success' | 'error' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
}

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<ToastItem[]>([])
  let nextId = 1

  function toast(type: ToastType, message: string, duration = 3500) {
    const id = nextId++
    toasts.value.push({ id, type, message })
    window.setTimeout(() => dismiss(id), duration)
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter(t => t.id !== id)
  }

  return { toasts, toast, dismiss }
})