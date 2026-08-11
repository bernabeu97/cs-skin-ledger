import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from './views/DashboardView.vue'
import TradesView from './views/TradesView.vue'
import OtherCostsView from './views/OtherCostsView.vue'
import SettingsView from './views/SettingsView.vue'
import MarketView from './views/MarketView.vue'
import LoginView from './views/LoginView.vue'
import { useAuthStore } from './stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/trades', name: 'trades', component: TradesView },
    { path: '/costs', name: 'costs', component: OtherCostsView },
    { path: '/market', name: 'market', component: MarketView },
    { path: '/settings', name: 'settings', component: SettingsView }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.load()
  if (to.meta.public) {
    return auth.authenticated && to.name === 'login' ? { name: 'dashboard' } : true
  }
  if (!auth.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})
