import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
    { path: '/security', name: 'security', component: () => import('./views/SecurityView.vue') },
    { path: '/', name: 'dashboard', component: () => import('./views/DashboardView.vue') },
    { path: '/trades', name: 'trades', component: () => import('./views/TradesView.vue') },
    { path: '/costs', name: 'costs', component: () => import('./views/OtherCostsView.vue') },
    { path: '/market', name: 'market', component: () => import('./views/MarketView.vue') },
    { path: '/report', name: 'report', component: () => import('./views/MonthlyReportView.vue') },
    { path: '/settings', name: 'settings', component: () => import('./views/SettingsView.vue') },
    { path: '/admin', name: 'admin', component: () => import('./views/AdminView.vue'), meta: { admin: true } }
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
  if ((auth.mfaSetupRequired || auth.passwordChangeRequired) && to.name !== 'security') {
    return { name: 'security' }
  }
  if (to.meta.admin && !auth.isAdmin) return { name: 'dashboard' }
  return true
})
