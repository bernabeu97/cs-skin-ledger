<script setup lang="ts">
import AppToast from './components/AppToast.vue'
import AlertPoller from './components/AlertPoller.vue'
import CommandPalette from './components/CommandPalette.vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useUiStore } from './stores/ui'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()

function openPalette() {
  window.dispatchEvent(new Event('open-command-palette'))
}
</script>

<template>
  <header v-if="route.name !== 'login'" class="topbar">
    <div class="topbar-inner">
      <router-link to="/" class="brand"><span class="brand-mark">SL</span><span>SkinLedger</span></router-link>
      <nav class="nav" aria-label="主导航">
        <router-link to="/" class="nav-link">概览</router-link>
        <router-link to="/trades" class="nav-link">账本</router-link>
        <router-link to="/costs" class="nav-link">收支</router-link>
        <router-link to="/market" class="nav-link">行情</router-link>
        <router-link to="/settings" class="nav-link">设置</router-link>
        <router-link v-if="auth.isAdmin" to="/admin" class="nav-link">管理</router-link>
      </nav>
      <div class="account">
        <button type="button" class="icon-button palette-hint" title="命令面板 (Ctrl+K)" aria-label="打开命令面板" @click="openPalette">⌘</button>
        <button class="icon-button" type="button" :aria-label="ui.theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'" :title="ui.theme === 'dark' ? '浅色主题' : '深色主题'" @click="ui.toggleTheme">{{ ui.theme === 'dark' ? '☀' : '☾' }}</button>
        <router-link to="/security" class="account-name">{{ auth.username }}</router-link>
        <button type="button" class="logout" @click="auth.logout">退出</button>
      </div>
    </div>
  </header>
  <main :class="route.name === 'login' ? '' : 'container'">
    <router-view />
  </main>
  <AppToast />
  <CommandPalette />
  <AlertPoller />
</template>

<style scoped>
.topbar {
  position: sticky; top: 0; z-index: 100;
  background: color-mix(in srgb, var(--bg) 78%, transparent);
  backdrop-filter: blur(18px); -webkit-backdrop-filter: blur(18px);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 1px 0 var(--accent-glow);
}
.topbar-inner { max-width: 1240px; margin: 0 auto; padding: 0 20px; height: 58px; display: flex; align-items: center; gap: 28px; }
.brand { display: flex; align-items: center; gap: 9px; color: var(--text); font-weight: 680; font-size: 14px; letter-spacing: -.01em; text-decoration: none; white-space: nowrap; }
.brand-mark {
  display: grid; place-items: center; width: 28px; height: 28px; color: #04120f;
  background: linear-gradient(135deg, var(--accent), var(--accent-hover));
  border-radius: 8px; font: 750 10px/1 var(--font-mono); letter-spacing: .03em;
  box-shadow: 0 0 14px var(--accent-glow);
}
.nav { display: flex; gap: 3px; }
.account { margin-left: auto; display: flex; align-items: center; gap: 6px; }
.account-name { color: var(--text-secondary); font-size: 12px; max-width: 140px; overflow: hidden; text-overflow: ellipsis; text-decoration: none; }
.logout, .icon-button { color: var(--text-secondary); border: 0; background: transparent; min-height: 34px; padding: 7px 8px; border-radius: var(--radius-sm); cursor: pointer; }
.icon-button { width: 34px; font-size: 17px; line-height: 1; }
.logout:hover, .icon-button:hover { color: var(--text); background: var(--surface-muted); }
.logout:focus-visible, .icon-button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.nav-link { color: var(--text-secondary); text-decoration: none; font-size: 13px; padding: 8px 10px; border-radius: var(--radius-sm); transition: color var(--motion-fast) ease, background var(--motion-fast) ease, box-shadow var(--motion-fast) ease; }
.nav-link:hover { color: var(--text); background: var(--surface-muted); }
.nav-link:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.nav-link.router-link-active { color: var(--accent); background: var(--accent-soft); box-shadow: inset 0 -2px 0 var(--accent); }
.container { max-width: 1240px; margin: 0 auto; padding: 26px 20px 52px; }
@media (max-width: 760px) {
  .topbar { position: fixed; top: auto; bottom: 0; width: 100%; border-top: 1px solid var(--border); border-bottom: 0; box-shadow: 0 -1px 0 var(--accent-glow); }
  .topbar-inner { height: 62px; padding: 0 8px; gap: 4px; }
  .brand, .account-name, .logout { display: none; }
  .nav { order: 1; flex: 1; justify-content: space-around; min-width: 0; }
  .account { order: 2; margin-left: 0; }
  .nav-link { flex: 1; min-width: 0; padding: 9px 5px; text-align: center; font-size: 11px; }
  .nav-link.router-link-active { box-shadow: inset 0 2px 0 var(--accent); }
  .container { padding: 18px 12px 90px; }
}
</style>
