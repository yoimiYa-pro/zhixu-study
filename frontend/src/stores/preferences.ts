import { defineStore } from 'pinia'
import { ref } from 'vue'

export const usePreferences = defineStore('preferences', () => {
  const dark = ref(localStorage.getItem('theme') === 'dark' ||
    (!localStorage.getItem('theme') && window.matchMedia('(prefers-color-scheme: dark)').matches))
  function applyTheme() { document.documentElement.classList.toggle('dark', dark.value) }
  function toggleTheme() {
    dark.value = !dark.value
    localStorage.setItem('theme', dark.value ? 'dark' : 'light')
    applyTheme()
  }
  applyTheme()
  return { dark, toggleTheme }
})
