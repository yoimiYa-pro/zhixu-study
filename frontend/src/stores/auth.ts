import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, refreshSession, setToken, setTimezone, type UserProfile } from '../lib/api'

export const useAuth = defineStore('auth', () => {
  const authenticated = ref(false), initialized = ref(false), username = ref(''), avatar = ref('')
  function updateProfile(profile: UserProfile) { username.value = profile.username; avatar.value = profile.avatar || '' }
  function clear() { setToken(''); authenticated.value = false; username.value = ''; avatar.value = '' }
  async function restore() {
    try { const result = await refreshSession(); authenticated.value = true; updateProfile(result) }
    catch { clear() }
    initialized.value = true
  }
  async function login(name: string, password: string) {
    const { data } = await api.post('/auth/login', { username: name, password })
    setToken(data.accessToken); updateProfile(data); authenticated.value = true; initialized.value = true
    setTimezone(data.timezone)
  }
  async function logout() { try { await api.post('/auth/logout', {}) } finally { clear() } }
  return { authenticated, initialized, username, avatar, updateProfile, restore, login, logout, clear }
})
