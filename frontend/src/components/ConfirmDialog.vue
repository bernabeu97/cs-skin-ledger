<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{
  title: string
  message: string
  confirmText?: string
  danger?: boolean
}>()

const emit = defineEmits<{ (e: 'confirm'): void; (e: 'cancel'): void }>()
const cancelRef = ref<HTMLButtonElement | null>(null)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('cancel')
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  cancelRef.value?.focus()
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="dialog-mask" @click.self="emit('cancel')">
    <div class="dialog-panel confirm-panel" role="dialog" aria-modal="true" :aria-label="title">
      <h3 class="confirm-title">{{ title }}</h3>
      <p class="confirm-message">{{ message }}</p>
      <div class="confirm-actions">
        <button type="button" class="btn" ref="cancelRef" @click="emit('cancel')">取消</button>
        <button type="button" class="btn" :class="danger ? 'btn-danger' : 'btn-primary'" @click="emit('confirm')">
          {{ confirmText ?? '确认' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirm-panel { max-width: 420px; padding: 20px; }
.confirm-title { margin: 0 0 8px; font-size: 16px; font-weight: 650; }
.confirm-message { margin: 0 0 18px; font-size: 13px; color: var(--text-secondary); line-height: 1.6; }
.confirm-actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>