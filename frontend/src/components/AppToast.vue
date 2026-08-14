<script setup lang="ts">
import { useUiStore } from '../stores/ui'

const ui = useUiStore()
</script>

<template>
  <div class="toast-region" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="t in ui.toasts" :key="t.id" class="toast" :class="`toast-${t.type}`" role="status">
        <span class="toast-dot" aria-hidden="true"></span>
        <span class="toast-msg">{{ t.message }}</span>
        <button v-if="t.action" type="button" class="toast-action" @click="t.action!.onClick(); ui.dismiss(t.id)">
          {{ t.action.label }}
        </button>
        <button type="button" class="toast-close" aria-label="关闭" @click="ui.dismiss(t.id)">×</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-region {
  position: fixed; top: 16px; right: 16px; z-index: 1000;
  display: flex; flex-direction: column; gap: 8px;
  max-width: min(380px, calc(100vw - 32px));
}
.toast {
  display: flex; align-items: flex-start; gap: 8px;
  background: var(--surface); border: 1px solid var(--border);
  border-left: 3px solid var(--accent); border-radius: var(--radius);
  padding: 10px 12px; box-shadow: var(--shadow-lg); font-size: 13px;
}
.toast-success { border-left-color: var(--success); }
.toast-error { border-left-color: var(--danger); }
.toast-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); margin-top: 5px; flex: none; }
.toast-success .toast-dot { background: var(--success); }
.toast-error .toast-dot { background: var(--danger); }
.toast-info .toast-dot { background: var(--text-muted); }
.toast-msg { flex: 1; line-height: 1.45; white-space: pre-wrap; word-break: break-word; }
.toast-close { border: none; background: none; color: var(--text-muted); cursor: pointer; font-size: 16px; line-height: 1; padding: 2px 4px; border-radius: 4px; }
.toast-close:hover { color: var(--text); background: rgba(16,24,40,.06); }
.toast-action {
  flex: none; border: 1px solid var(--accent); background: var(--accent-soft); color: var(--accent);
  border-radius: var(--radius-sm); padding: 3px 9px; font-size: 12px; font-weight: 600; cursor: pointer;
}
.toast-action:hover { background: var(--accent); color: #fff; }
.toast-enter-active, .toast-leave-active { transition: all .18s ease; }
.toast-enter-from { opacity: 0; transform: translateY(-6px); }
.toast-leave-to { opacity: 0; transform: translateX(10px); }
</style>
