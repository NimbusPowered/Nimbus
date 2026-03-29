import DefaultTheme from 'vitepress/theme'
import HomeAnimations from './HomeAnimations.vue'
import { h } from 'vue'
import './custom.css'

export default {
  extends: DefaultTheme,
  Layout() {
    return h(DefaultTheme.Layout, null, {
      'home-hero-before': () => h(HomeAnimations),
    })
  },
}
