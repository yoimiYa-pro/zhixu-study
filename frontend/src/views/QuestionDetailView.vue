<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import AppIcon from '../components/AppIcon.vue'
import QuestionAnalysisContent from '../components/QuestionAnalysisContent.vue'
import { useRoute, useRouter } from 'vue-router'
import { api, errorText, reasons, taskLabel, formatTime, type Question } from '../lib/api'
const route = useRoute(), router = useRouter(), question = ref<Question | null>(null), error = ref(''), cause = ref(''), similar = ref<Question[]>([]), searched = ref(false), searching = ref(false)
const requestingAnalysis = ref(false)
const analyzing = computed(() => requestingAnalysis.value || ['PENDING', 'PROCESSING'].includes(question.value?.aiStatus || ''))
let timer: ReturnType<typeof setTimeout> | undefined
async function load() { clearTimeout(timer); try { question.value = (await api.get<Question>(`/questions/${route.params.id}`)).data; cause.value = question.value.mistakeReason || ''; if (['PENDING','PROCESSING'].includes(question.value.aiStatus || '')) timer = setTimeout(load, 3000) } catch (e) { error.value = errorText(e) } }
async function saveCause() { try { await api.patch(`/questions/${route.params.id}/mistake`, { reason: cause.value }); await load() } catch (e) { error.value = errorText(e) } }
async function search() { searching.value = true; error.value = ''; try { similar.value = (await api.post<Question[]>(`/questions/${route.params.id}/similar`, {}, { timeout: 90000 })).data; searched.value = true } catch (e) { error.value = errorText(e) } finally { searching.value = false } }
async function analyze() {
  if (analyzing.value) return
  requestingAnalysis.value = true; error.value = ''
  try {
    await api.post(`/questions/${route.params.id}/analyze`, {})
    await load()
  } catch (e) { error.value = errorText(e) }
  finally { requestingAnalysis.value = false }
}
async function remove() { if (!confirm('删除这道题目及其复习安排？学习记录会保留。')) return; try { await api.delete(`/questions/${route.params.id}`); router.push('/questions') } catch (e) { error.value = errorText(e) } }
onMounted(load); onUnmounted(() => clearTimeout(timer))
</script>
<template><p v-if="error" class="error" role="alert">{{ error }}</p><template v-if="question"><div class="page-heading"><div><span class="eyebrow">{{ question.questionType }} · {{ question.difficulty }}</span><h1>错题复盘</h1><p class="muted">下次复习：{{ formatTime(question.nextReviewAt) }}</p></div><div class="actions"><RouterLink class="button secondary" to="/questions"><AppIcon name="arrow-left" :size="16" />返回错题笔记</RouterLink><RouterLink class="button secondary" :to="`/questions/${question.id}/edit`">编辑题目</RouterLink><button class="text-button danger" @click="remove">删除</button></div></div><section class="panel"><p class="reading">{{ question.content }}</p><div v-for="(option,key) in question.options" :key="key" class="option" :class="{ correct: key === question.correctAnswer, wrong: key === question.userAnswer && key !== question.correctAnswer }"><strong>{{ key }}</strong><span>{{ option }}</span></div><div class="answer-strip"><span>正确答案：<b>{{ question.correctAnswer }}</b></span><span>我的答案：{{ question.userAnswer || '未作答' }}</span></div><h3>参考解析</h3><MarkdownContent v-if="question.explanation" class="reading" :content="question.explanation" /><p v-else class="reading">尚未填写参考解析。</p><p class="muted">来源：{{ question.source || '用户录入，来源未填写' }} {{ question.year || '' }} {{ question.region }}</p><div class="actions"><span v-for="point in question.knowledgePoints" :key="point" class="tag">{{ point }}</span></div></section><section class="panel section-gap ai-analysis-panel">
  <div class="panel-heading">
    <h2>AI 学习分析</h2>
    <div class="actions">
      <span class="badge" role="status">{{ taskLabel(question.aiStatus) }}</span>
      <button class="button secondary" :disabled="analyzing" @click="analyze">{{ analyzing ? '正在讲解…' : question.analysis ? '重新讲解' : '开始讲解' }}</button>
    </div>
  </div>
  <p v-if="analyzing && question.analysis" class="muted" role="status">新版讲解正在生成，先保留上次分析供你查看。</p>
  <p v-else-if="question.aiStatus === 'FAILED' && question.analysis" class="muted" role="status">本次讲解未完成，上次分析仍可查看。可重新讲解，或到处理进度页检查配置。</p>
  <QuestionAnalysisContent v-if="question.analysis" :analysis="question.analysis" />
  <p v-else class="empty">{{ question.aiStatus === 'FAILED' ? '分析尚未完成，可开始讲解，或在处理进度页面检查配置并重试。题目已安全保存。' : analyzing ? '分析将在后台进行，完成后自动显示。' : '点击开始讲解，一步步理解这道题。' }}</p>
  <RouterLink class="text-button analysis-task-link" to="/tasks">查看处理进度</RouterLink>
</section><section v-if="question.mistake" class="panel section-gap"><h2>我的错因确认</h2><p class="muted">根据自己的实际情况确认，作为复习时的提醒。</p><div class="toolbar"><select v-model="cause" aria-label="错因"><option disabled value="未确认">未确认</option><option v-for="reason in reasons" :key="reason">{{ reason }}</option></select><button class="button" @click="saveCause" :disabled="!reasons.includes(cause)">确认错因</button><span v-if="question.mistakeConfirmed" class="tag">已确认</span></div></section><section class="panel section-gap"><div class="panel-heading"><h2>举一反三</h2><button class="button secondary" :disabled="searching" @click="search">{{ searching ? '正在检索…' : '寻找相似题' }}</button></div><p v-if="searched && !similar.length" class="empty">知识库中暂时没有其他相似题，继续积累题目后再试。</p><RouterLink v-for="item in similar" :key="item.id" :to="`/questions/${item.id}`" class="list-row"><span class="tag">相似度 {{ ((item.score || 0) * 100).toFixed(1) }}%</span><p class="reading clamp">{{ item.content }}</p><small>{{ item.knowledgePoints.join(' · ') }} · {{ item.source || '来源未提供' }} · 答案 {{ item.correctAnswer }}</small></RouterLink></section></template></template>

<style scoped>
.ai-analysis-panel > .panel-heading { flex-wrap:wrap; gap:12px; }
.ai-analysis-panel > .panel-heading h2 { margin:0; }
.analysis-task-link { display:inline-flex; margin-top:24px; }
</style>
