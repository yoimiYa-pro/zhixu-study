<script setup lang="ts">
import PageHeader from '../components/PageHeader.vue'
import EmptyState from '../components/EmptyState.vue'
import { onMounted, ref } from 'vue'
import { api, errorText, formatTime } from '../lib/api'
const records = ref<{ id: string; kind: string; quantity: number; wrongCount: number; timeSpent: number; note: string; studiedAt: string; questionId: string | null }[]>([]), error = ref(''), loading = ref(true)
const names: Record<string,string> = { PRACTICE: '练习', READING: '阅读 / 整理', REVIEW: '错题复习', REVIEW_SKIP: '跳过复习' }
onMounted(async () => { try { records.value = (await api.get('/study-records')).data } catch (e) { error.value = errorText(e) } finally { loading.value = false } })
</script>
<template><PageHeader title="学习记录"><template #description><p class="muted">最近 100 条学习与复习记录</p></template><RouterLink class="text-button" to="/">返回今日学习</RouterLink></PageHeader><p v-if="error" class="error">{{ error }}</p><p v-if="loading" class="loading-state" role="status">正在读取学习记录…</p><EmptyState v-else-if="!records.length && !error" title="暂无学习记录" description="练习、阅读和复习记录会显示在这里。" icon="clock"><RouterLink class="button" to="/">去记录学习</RouterLink></EmptyState><section v-if="records.length" class="panel record-journal"><div v-for="record in records" :key="record.id" class="list-row"><h3>{{ names[record.kind] || record.kind }}</h3><p class="muted">{{ formatTime(record.studiedAt) }} · {{ record.quantity }} 道 · 错题 {{ record.wrongCount }} 道 · {{ Math.round(record.timeSpent / 60) }} 分钟</p><p class="reading">{{ record.note }}</p><RouterLink v-if="record.questionId" class="text-button" :to="'/questions/' + record.questionId">查看关联题目</RouterLink></div></section></template>
