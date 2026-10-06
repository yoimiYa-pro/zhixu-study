<script setup lang="ts">
import { usePreferences } from './stores/preferences'
import { useAuth } from './stores/auth'
import { useRoute, useRouter } from 'vue-router'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import ModelSwitcher from './components/ModelSwitcher.vue'
import AppIcon from './components/AppIcon.vue'
import StudySketch from './components/StudySketch.vue'
import UserAvatar from './components/UserAvatar.vue'
import AccountSettings from './components/AccountSettings.vue'
const preferences = usePreferences()
const auth = useAuth(), route = useRoute(), router = useRouter()
const navigation = ref<HTMLElement | null>(null)
const settingsOpen = ref(false)
type NavigationLink = [path: string, label: string, icon: string]
const groups: { label: string; links: NavigationLink[] }[] = [
  { label: '学习与练习', links: [['/', '今日学习', 'home'], ['/questions', '错题笔记', 'book'], ['/reviews', '每日复习', 'review'], ['/knowledge', '知识体系', 'network']] },
  { label: '阅读与积累', links: [['/news', '每日时政', 'news'], ['/idioms', '成语与词语', 'words'], ['/materials', '申论素材', 'folder']] },
  { label: '回顾与计划', links: [['/statistics', '学习统计', 'chart'], ['/records', '学习记录', 'clock'], ['/weekly', '学习周报', 'calendar']] },
  { label: '学习工具', links: [['/chat', 'AI 学习助手', 'chat'], ['/essay-assistant', 'AI 申论助手', 'pen'], ['/tasks', '处理进度', 'tasks']] },
]
const currentGroup = computed(() => groups.find(group => group.links.some(([path]) => path === '/' ? route.path === '/' : route.path.startsWith(path))))
const currentPage = computed(() => route.path === '/login' ? '登录' : currentGroup.value?.links.find(([path]) => path === '/' ? route.path === '/' : route.path.startsWith(path))?.[1] || '知序')
watch(currentPage, value => { document.title = value + ' · 知序' }, { immediate: true })
watch(() => route.path, async () => {
  await nextTick()
  navigation.value?.querySelector('.active')?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}, { immediate: true, flush: 'post' })
const expired = () => { settingsOpen.value = false; auth.clear(); router.push('/login') }
onMounted(() => window.addEventListener('session-expired', expired))
onUnmounted(() => window.removeEventListener('session-expired', expired))
async function logout() { try { await auth.logout() } finally { router.push('/login') } }
</script>

<template>
  <a v-if="route.path !== '/login'" class="skip-link" href="#main-content">跳到主要内容</a>
  <div v-if="route.path === '/login'" class="login-shell"><RouterView /></div>
  <div v-else class="workspace study-workspace" :class="{ 'is-chat': route.path === '/chat', 'is-study-overview': route.path === '/' }">
    <aside class="sidebar">
      <RouterLink to="/" class="brand" aria-label="知序公考学习首页"><span>知序<small>公考学习</small></span><StudySketch class="brand-sketch" name="book" /></RouterLink>
      <nav ref="navigation" aria-label="主要导航"><div v-for="group in groups" :key="group.label" class="nav-group"><p class="nav-caption">{{ group.label }}</p><RouterLink v-for="[path, label, icon] in group.links" :key="path" :to="path" :class="{ active: path === '/' ? route.path === '/' : route.path.startsWith(path) }" :aria-current="(path === '/' ? route.path === '/' : route.path.startsWith(path)) ? 'page' : undefined"><AppIcon :name="icon" :size="18" /><span>{{ label }}</span></RouterLink></div></nav>
      <div class="sidebar-bottom"><button class="theme-button" @click="preferences.toggleTheme"><AppIcon :name="preferences.dark ? 'sun' : 'moon'" :size="16" />{{ preferences.dark ? '切换浅色' : '切换深色' }}</button></div>
    </aside>
    <div class="workspace-main"><header class="workspace-header"><div class="study-breadcrumb"><span>{{ currentGroup?.label }}</span></div><div class="header-controls"><ModelSwitcher /><div class="account-control"><button class="account-name account-settings-trigger" aria-label="打开个人设置" title="个人设置" @click="settingsOpen = true"><UserAvatar :src="auth.avatar" :name="auth.username" /><span>{{ auth.username }}</span></button><button class="text-button logout-button" @click="logout">退出登录</button></div></div></header><main id="main-content" class="page" :class="{ 'page-chat': route.path === '/chat', ['page-' + route.path.split('/')[1]]: route.path !== '/' }" tabindex="-1"><RouterView :key="route.path === '/chat' ? route.path : route.fullPath" /></main></div>
    <AccountSettings v-model:open="settingsOpen" />
  </div>
</template>
