<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { useAuth } from '../stores/auth'
import { errorText } from '../lib/api'
import AppIcon from '../components/AppIcon.vue'
import { usePreferences } from '../stores/preferences'
const username = ref(''), password = ref(''), error = ref(''), busy = ref(false), showPassword = ref(false)
const auth = useAuth(), router = useRouter(), preferences = usePreferences()
const route = useRoute()
async function submit() { busy.value = true; error.value = ''; try { await auth.login(username.value, password.value); password.value = ''; await router.push('/') } catch (e) { error.value = errorText(e) } finally { busy.value = false } }
</script>
<template>
  <aside class="login-art" aria-label="知序公考学习">
    <div class="login-brand">知序<small>公考学习</small></div>
    <img src="/images/study-paper-mountains.webp" alt="" class="login-book" fetchpriority="high">
  </aside>
  <div class="login-form-pane">
    <button class="theme-button login-theme" @click="preferences.toggleTheme"><AppIcon :name="preferences.dark ? 'sun' : 'moon'" :size="20" /><span class="sr-only">{{ preferences.dark ? '切换浅色' : '切换深色' }}</span></button>
    <section class="login-card">
    <h1>登录知序</h1><p class="muted">登录后查看错题和复习安排。</p>
    <p v-if="route.query.passwordChanged === '1'" class="notice" role="status">密码已修改，请使用新密码重新登录。</p>
    <form @submit.prevent="submit"><label>用户名<input v-model="username" required autocomplete="username" maxlength="100" placeholder="用户名" :disabled="busy"></label><label for="login-password">密码</label><div class="password-field"><input id="login-password" v-model="password" required :type="showPassword ? 'text' : 'password'" autocomplete="current-password" maxlength="72" placeholder="密码" :disabled="busy"><button type="button" class="password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><AppIcon :name="showPassword ? 'eye-off' : 'eye'" :size="18" /></button></div><p v-if="error" class="error" role="alert">{{ error }}</p><button class="button full" :disabled="busy">{{ busy ? '正在登录…' : '登录' }}</button></form>
    </section>
  </div>
</template>
