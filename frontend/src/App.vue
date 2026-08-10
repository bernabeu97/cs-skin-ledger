<script setup lang="ts">
import AppToast from './components/AppToast.vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const auth = useAuthStore()
</script>

<template>
  <header v-if="route.name !== 'login'" class="topbar">
    <div class="topbar-inner">
      <span class="brand">CS 饰品买卖统计</span>
      <nav class="nav">
        <router-link to="/" class="nav-link">仪表盘</router-link>
        <router-link to="/trades" class="nav-link">饰品账本</router-link>
        <router-link to="/costs" class="nav-link">其他收支</router-link>
        <router-link to="/settings" class="nav-link">设置</router-link>
      </nav>
      <div class="account">
        <span class="account-name">{{ auth.username }}</span>
        <button type="button" class="logout" @click="auth.logout">退出</button>
      </div>
    </div>
  </header>
  <main :class="route.name === 'login' ? '' : 'container'">
    <router-view />
  </main>
  <AppToast />
</template>

<style scoped>
.topbar { position: sticky; top: 0; z-index: 100; background: #111318; border-bottom: 1px solid rgba(255,255,255,.07); }
.topbar-inner { max-width: 1200px; margin: 0 auto; padding: 0 20px; height: 52px; display: flex; align-items: center; gap: 28px; }
.brand { color: #fff; font-weight: 650; font-size: 14px; letter-spacing: .01em; white-space: nowrap; }
.nav { display: flex; gap: 4px; }
.account { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.account-name { color: #d6dae2; font-size: 12px; max-width: 140px; overflow: hidden; text-overflow: ellipsis; }
.logout { color: #aeb3bf; border: 0; background: transparent; padding: 7px 8px; border-radius: var(--radius-sm); cursor: pointer; }
.logout:hover { color: #fff; background: rgba(255,255,255,.08); }
.logout:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.nav-link { color: #aeb3bf; text-decoration: none; font-size: 13px; padding: 7px 10px; border-radius: var(--radius-sm); transition: color var(--motion-fast) ease, background var(--motion-fast) ease, box-shadow var(--motion-fast) ease; }
.nav-link:hover { color: #fff; background: rgba(255,255,255,.08); }
.nav-link:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.nav-link.router-link-active { color: #fff; background: rgba(255,255,255,.1); box-shadow: inset 0 -2px 0 var(--accent); }
.container { max-width: 1200px; margin: 0 auto; padding: 24px 20px 48px; }
@media (max-width: 640px) {
  .topbar-inner { padding: 0 12px; gap: 12px; }
  .container { padding: 16px 12px 40px; }
  .brand { display: none; }
  .account-name { display: none; }
}
</style>
