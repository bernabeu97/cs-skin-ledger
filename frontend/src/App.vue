<script setup lang="ts">
</script>

<template>
  <header class="nav">
    <span class="brand">CS 饰品买卖统计</span>
    <nav>
      <router-link to="/">仪表盘</router-link>
      <router-link to="/trades">交易记录</router-link>
    </nav>
  </header>
  <main class="container">
    <router-view />
  </main>
</template>

<style>
.nav { display: flex; align-items: center; gap: 24px; padding: 12px 24px; background: #1b1b1f; color: #fff; }
.nav .brand { font-weight: 600; }
.nav a { color: #c9c9d4; text-decoration: none; margin-right: 12px; }
.nav a.router-link-active { color: #fff; font-weight: 600; }
.container { max-width: 1100px; margin: 24px auto; padding: 0 16px; }
</style>