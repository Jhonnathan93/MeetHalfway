import { createApp } from 'vue'
import App from './App.vue'
import { i18n } from './i18n'
import { applyDefaultTheme } from './composables/useTheme'
import './styles/theme.css'
import './styles/base.css'

applyDefaultTheme()

createApp(App).use(i18n).mount('#app')
