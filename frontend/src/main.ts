import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'

const isTauri = '__TAURI_INTERNALS__' in window

async function bootstrap() {
  if (isTauri) {
    const { default: DesktopApp } = await import('./desktop/DesktopApp.vue')
    createApp(DesktopApp).use(createPinia()).mount('#app')
  } else {
    const [{ default: App }, { router }] = await Promise.all([import('./App.vue'), import('./router')])
    createApp(App).use(createPinia()).use(router).mount('#app')
  }
}

void bootstrap()
