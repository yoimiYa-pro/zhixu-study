<script setup lang="ts">
import PageHeader from '../components/PageHeader.vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, errorText, taskLabel, formatTime, type AiTask } from '../lib/api'
import AppIcon from '../components/AppIcon.vue'
import MetricCard from '../components/MetricCard.vue'
import ProgressMeter from '../components/ProgressMeter.vue'
import StudyChart from '../components/StudyChart.vue'
import TokenUsage from '../components/TokenUsage.vue'
const tasks = ref<AiTask[]>([]), error = ref(''), message = ref(''), rebuilding = ref(false), filter = ref('')
const configured = ref<{ llmConfigured: boolean; embeddingConfigured: boolean; activeModel?: string } | null>(null)
const usageRefresh = ref(0)
const overviewOpen = ref(false)
const names: Record<string, string> = { ANALYZE_QUESTION: '错题分析', INDEX_DOCUMENT: '知识索引', DELETE_DOCUMENT: '索引清理', NEWS_FETCH: '时政抓取', ANALYZE_NEWS: '时政整理', ANALYZE_NEWS_BATCH: '时政日报整理', WEEKLY_REPORT: '学习周报' }
const codes: Record<string, string> = { LLM_NOT_CONFIGURED: '模型尚未配置', EMBEDDING_NOT_CONFIGURED: '向量模型尚未配置', PROVIDER_UNAVAILABLE: '模型服务暂时不可用', QDRANT_UNAVAILABLE: '知识索引暂时不可用', AI_INVALID_OUTPUT: '模型输出未通过校验', AI_INVALID_JSON: '模型输出格式不正确', NEWS_FEEDS_EMPTY: '尚未配置时政来源', NEWS_FETCH_FAILED: '时政来源抓取失败', NEWS_SOURCE_LIMIT: '时政来源数量超过上限', NEWS_URL_REJECTED: '新闻来源地址未通过检查' }
const statuses = ['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED']
const counts = computed(() => Object.fromEntries(statuses.map(status => [status, tasks.value.filter(task => task.status === status).length])) as Record<string, number>)
const visible = computed(() => tasks.value.filter(task => !filter.value || task.status === filter.value))
const chart = computed(() => ({ tooltip: { trigger: 'item' }, legend: { bottom: 0 }, color: ['#c79951', '#6597c1', '#599d92', '#bd7786'], series: [{ type: 'pie', radius: ['45%', '68%'], center: ['50%', '43%'], label: { show: false }, data: statuses.map(status => ({ name: taskLabel(status), value: counts.value[status] })).filter(item => item.value > 0) }] }))
let timer: ReturnType<typeof setInterval> | undefined, loading = false
async function load() {
  if (loading) return
  loading = true
  try { tasks.value = (await api.get<AiTask[]>('/ai-tasks')).data; error.value = '' }
  catch (problem) { error.value = errorText(problem) }
  finally { loading = false }
}
async function rebuild() { rebuilding.value = true; try { const { data } = await api.post('/maintenance/reindex'); message.value = '已安排 ' + data.queued + ' 条内容重新建立知识索引。'; await load() } catch (problem) { error.value = errorText(problem) } finally { rebuilding.value = false } }
async function retry(id: string) { try { await api.post('/ai-tasks/' + id + '/retry', {}); await load() } catch (problem) { error.value = errorText(problem) } }
function refresh() { void load(); usageRefresh.value++ }
onMounted(() => { void load(); api.get('/ai/status').then(response => { configured.value = response.data }).catch(() => {}); timer = setInterval(() => { if (!document.hidden) void load() }, 5000) })
onUnmounted(() => clearInterval(timer))
</script>
<template>
  <PageHeader title="处理进度"><template #description><p class="muted">最近 100 项任务 · 模型用量统计</p></template><div class="actions"><button class="button secondary" :disabled="rebuilding" @click="rebuild"><AppIcon name="network" :size="16" />重建知识索引</button><button class="button secondary" @click="refresh"><AppIcon name="refresh" :size="16" />刷新</button></div></PageHeader>
  <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="message" class="notice" role="status">{{ message }}</p>
  <div class="stat-grid"><MetricCard label="等待处理" :value="counts.PENDING" unit="项" icon="clock" tone="amber" /><MetricCard label="正在处理" :value="counts.PROCESSING" unit="项" icon="spark" tone="blue" /><MetricCard label="已完成" :value="counts.COMPLETED" unit="项" icon="check" /><MetricCard label="需要重试" :value="counts.FAILED" unit="项" icon="warning" tone="red" /></div>
  <TokenUsage :refresh-key="usageRefresh" />
  <details class="panel task-overview" @toggle="overviewOpen = ($event.target as HTMLDetailsElement).open"><summary>任务状态与完成情况 <span class="muted">{{ counts.COMPLETED }} / {{ tasks.length }} 项已完成</span></summary><div class="content-grid task-summary"><section class="panel"><h2><AppIcon name="chart" />任务状态分布</h2><StudyChart v-if="tasks.length && overviewOpen" :option="chart" label="最近一百项后台任务的等待、处理、完成和失败分布" /><p v-else-if="!tasks.length" class="empty">还没有后台任务。</p></section><section class="panel"><h2><AppIcon name="tasks" />完成情况</h2><ProgressMeter :value="counts.COMPLETED" :total="tasks.length" label="最近任务完成比例" :detail="counts.COMPLETED + ' / ' + tasks.length + ' 项已完成'" /><p class="muted">进行中的任务会自动更新；失败任务可以在下方重试。</p><div v-if="configured" class="actions"><span class="status-badge" :class="configured.llmConfigured ? 'completed' : 'pending'">学习模型 {{ configured.llmConfigured ? '已配置' : '未配置' }}</span><span class="status-badge" :class="configured.embeddingConfigured ? 'completed' : 'pending'">向量模型 {{ configured.embeddingConfigured ? '已配置' : '未配置' }}</span></div><p class="chart-caption">读取来源或等待模型回复时显示处理中；取得实际处理总量后展示进度百分比。</p></section></div></details>
  <div class="tag-filters task-filters" aria-label="任务状态筛选"><button :class="{ active: !filter }" :aria-pressed="!filter" @click="filter = ''">全部 <span>{{ tasks.length }}</span></button><button v-for="status in statuses" :key="status" :class="{ active: filter === status }" :aria-pressed="filter === status" @click="filter = status">{{ taskLabel(status) }} <span>{{ counts[status] }}</span></button></div>
  <section class="panel task-list" aria-label="任务列表"><p v-if="!visible.length" class="empty">{{ tasks.length ? '当前状态没有任务。' : '还没有需要处理的学习内容。' }}</p><article v-for="task in visible" :key="task.id" class="task-detail"><div class="task-item-heading"><h3><AppIcon :name="task.kind.includes('NEWS') ? 'news' : task.kind.includes('INDEX') ? 'network' : 'spark'" :size="18" /> {{ names[task.kind] || '学习内容整理' }}</h3><span class="status-badge" :class="task.status.toLowerCase()">{{ taskLabel(task.status) }}</span></div><div class="task-details-main"><small>{{ formatTime(task.createdAt) }} · 已尝试 {{ task.attempts }} 次</small><ProgressMeter :value="task.status === 'COMPLETED' ? 1 : task.progressDone || 0" :total="task.status === 'COMPLETED' ? 1 : task.progressTotal" :indeterminate="task.status === 'PROCESSING' && !task.progressTotal" :label="task.progressLabel || taskLabel(task.status)" :tone="task.status === 'FAILED' ? 'failed' : ''" :detail="task.progressTotal && task.status !== 'COMPLETED' ? (task.progressDone || 0) + ' / ' + task.progressTotal + ' 项已处理' : undefined" /><p v-if="task.errorCode" class="muted">{{ codes[task.errorCode] || '服务暂时无法完成处理' }}</p><p v-if="task.kind === 'NEWS_FETCH' && task.result" class="muted">新增 {{ task.result.stored }} 篇 · 去重 {{ task.result.duplicates || 0 }} 篇 · 失败来源 {{ task.result.failedFeeds || 0 }} 个</p><small v-if="task.completedAt">结束：{{ formatTime(task.completedAt) }}</small></div><button v-if="task.status === 'FAILED'" class="button secondary" @click="retry(task.id)">重试</button></article></section>
</template>
<style scoped>
.task-overview { margin-bottom:20px; padding:16px 0; }
.task-overview > summary { display:flex; align-items:center; gap:12px; cursor:pointer; font-size:13px; }
.task-overview > summary .muted { font-size:11px; }
.task-overview .task-summary { margin-top:18px; }
.task-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); column-gap:32px; padding-top:0; border-top:0; }
.task-list > .empty { grid-column:1/-1; }
.task-list .task-detail { display:flex; flex-direction:column; align-items:stretch; justify-content:flex-start; gap:8px; padding:23px 0; border-top:1px solid var(--border); min-width:0; }
.task-list .task-detail:nth-of-type(even) { border-left:1px solid var(--border); padding-left:30px; }
.task-list .task-item-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; flex:0; }
.task-list h3 { margin:0; font-size:15px; display:flex; align-items:center; gap:7px; }
.task-list small { display:block; margin-top:7px; color:var(--muted); font-size:11px; }
.task-list .task-details-main { flex:0; }
.task-list .task-details-main .progress-meter { max-width:none; }
.task-list .task-detail > .button { align-self:flex-start; margin-top:6px; }
@media(max-width:760px) {
  .task-list { grid-template-columns:minmax(0,1fr); }
  .task-list .task-detail:nth-of-type(even) { padding-left:0; border-left:0; }
  .task-overview > summary { flex-wrap:wrap; }
}
</style>
