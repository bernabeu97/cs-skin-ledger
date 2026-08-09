import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from './views/DashboardView.vue'
import TradesView from './views/TradesView.vue'
import OtherCostsView from './views/OtherCostsView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/trades', name: 'trades', component: TradesView },
    { path: '/costs', name: 'costs', component: OtherCostsView }
  ]
})