<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api, errorText, type TokenUsage } from '../lib/api'
import AppIcon from './AppIcon.vue'
import MetricCard from './MetricCard.vue'
import StudyChart from './StudyChart.vue'

const props = defineProps<{ refreshKey: number }>()
const days = ref(7), usage = ref<TokenUsage | null>(null), loading = ref(false), error = ref('')
const format = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
let controller: AbortController | undefined, timer: ReturnType<typeof setInterval> | undefined
async function load() {
  controller?.abort()
  const request = new AbortController()
  controller = request; loading.value = true; error.value = ''
  try {
    const { data } = await api.get<TokenUsage>('/ai/usage', { params: { days: days.value }, signal: request.signal })
    if (controller === request) usage.value = data
  } catch (cause) { if (!request.signal.aborted && controller === request) error.value = errorText(cause) }
  finally { if (controller === request) loading.value = false }
}
const dailyChart = computed(() => ({
  tooltip: { trigger: 'axis', renderMode: 'richText', confine: true }, legend: { bottom: 0 },
  grid: { left: 48, right: 18, top: 22, bottom: 55 },
  xAxis: { type: 'category', data: usage.value?.daily.map(day => day.date.slice(5)) || [] },
  yAxis: { type: 'value', minInterval: 1 },
  color: ['#5386b0', '#cf7955', '#599d92'],
  series: [
    { name: '输入', type: 'bar', stack: 'tokens', barMaxWidth: 24, data: usage.value?.daily.map(day => day.inputTokens) || [] },
    { name: '输出', type: 'bar', stack: 'tokens', barMaxWidth: 24, data: usage.value?.daily.map(day => day.outputTokens) || [] },
    { name: '总计', type: 'line', symbolSize: 5, data: usage.value?.daily.map(day => day.totalTokens) || [] },
  ],
}))
const topModels = computed(() => (usage.value?.models || []).slice(0, 8).reverse())
const chartHeight = computed(() => Math.max(220, topModels.value.length * 30 + 40))
const modelChart = computed(() => ({
  tooltip: { trigger: 'axis', renderMode: 'richText', confine: true },
  grid: { left: 142, right: 32, top: 16, bottom: 35 },
  xAxis: { type: 'value', minInterval: 1 },
  yAxis: { type: 'category', data: topModels.value.map(model => model.model + (model.kind === 'EMBEDDING' ? ' · 向量' : '')), axisLabel: { width: 130, overflow: 'truncate', fontSize: 11 } },
  series: [{ name: '已记录 Token', type: 'bar', barMaxWidth: 18, itemStyle: { color: '#5386b0', borderRadius: [0, 3, 3, 0] }, data: topModels.value.map(model => model.totalTokens) }],
}))
function onVisibility() { if (!document.hidden) void load() }
watch(days, () => { usage.value = null; void load() })
watch(() => props.refreshKey, () => { void load() })
onMounted(() => { void load(); timer = setInterval(() => { if (!document.hidden) void load() }, 30000); document.addEventListener('visibilitychange', onVisibility) })
onUnmounted(() => { controller?.abort(); clearInterval(timer); document.removeEventListener('visibilitychange', onVisibility) })
</script>
<template>
  <section class="panel token-usage" aria-labelledby="token-usage-title" :aria-busy="loading">
    <div class="panel-heading token-usage-heading"><div><h2 id="token-usage-title"><AppIcon name="chart" />模型 Token 用量</h2><p class="muted token-period">{{ usage ? usage.startDate + ' — ' + usage.endDate : '查看模型调用的实际用量' }}<span v-if="usage"> · {{ usage.timezone === 'Asia/Shanghai' ? '北京时间' : usage.timezone }}</span></p></div><div class="token-range"><label for="token-usage-range">统计范围</label><select id="token-usage-range" v-model.number="days"><option :value="1">今天</option><option :value="7">最近 7 天</option><option :value="30">最近 30 天</option></select></div></div>
    <p v-if="error" class="error" role="alert">{{ error }} <button class="text-button" :disabled="loading" @click="load">重新加载用量</button></p>
    <template v-if="usage">
      <div class="stat-grid token-metrics"><MetricCard label="已记录 Token" :value="usage.totals.calls && !usage.totals.reportedCalls ? '未上报' : format(usage.totals.totalTokens)" icon="chart" tone="blue" /><MetricCard label="已记录输入 Token" :value="format(usage.totals.inputTokens)" icon="arrow-right" tone="blue"><p v-if="usage.totals.cacheReportedCalls" class="metric-caption">缓存命中 {{ format(usage.totals.cachedInputTokens) }}</p></MetricCard><MetricCard label="已记录输出 Token" :value="format(usage.totals.outputTokens)" icon="pen" tone="amber" /><MetricCard label="模型调用" :value="format(usage.totals.calls)" unit="次" icon="spark"><p v-if="usage.totals.unreportedCalls" class="metric-caption">{{ format(usage.totals.unreportedCalls) }} 次未返回总用量</p></MetricCard></div>
      <p class="chart-caption token-recording-note">从功能上线后开始记录，历史调用没有用量记录。学习模型、向量模型及连接测试的用量均计入。</p>
      <p v-if="usage.totals.incompleteCalls" class="token-incomplete" role="status">{{ format(usage.totals.incompleteCalls) }} 次调用未返回完整用量，统计仅包含模型已上报的 Token。</p>
      <div v-if="usage.totals.reportedCalls" class="content-grid token-charts"><section class="token-chart"><h3>每日用量趋势</h3><StudyChart :option="dailyChart" :style="{ height: chartHeight + 'px' }" label="每日模型输入、输出和总 Token 用量趋势" /></section><section class="token-chart"><h3>模型用量分布</h3><StudyChart :option="modelChart" :style="{ height: chartHeight + 'px' }" label="模型已记录 Token 用量排行" /><p v-if="usage.models.length > 8" class="chart-caption">显示用量最高的 8 项模型，完整统计见下方明细。</p></section></div>
      <p v-else class="empty token-empty">{{ usage.totals.calls ? '已有模型调用，但暂未收到 Token 用量。' : '此时间范围还没有模型用量记录，调用模型后将在这里显示。' }}</p>
      <details v-if="usage.models.length" class="token-model-details"><summary>模型用量明细 <span class="muted">{{ usage.modelCount }} 项</span></summary><div class="token-model-table"><table><caption class="sr-only">按模型统计的调用次数和 Token 用量</caption><thead><tr><th scope="col">模型</th><th scope="col">类型</th><th scope="col">调用</th><th scope="col">已记录输入</th><th scope="col">已记录输出</th><th scope="col">已记录总 Token</th></tr></thead><tbody><tr v-for="model in usage.models" :key="model.providerKey + ':' + model.model + ':' + model.kind"><td><strong>{{ model.model }}</strong><small>{{ model.providerName }}</small></td><td>{{ model.kind === 'EMBEDDING' ? '向量模型' : '学习模型' }}</td><td>{{ format(model.calls) }}<small v-if="model.unreportedCalls">{{ model.unreportedCalls }} 次未上报</small></td><td>{{ format(model.inputTokens) }}<small v-if="model.cacheReportedCalls">缓存 {{ format(model.cachedInputTokens) }}</small></td><td>{{ format(model.outputTokens) }}</td><td>{{ model.reportedCalls ? format(model.totalTokens) : '未上报' }}</td></tr></tbody></table></div><p v-if="usage.modelCount > usage.models.length" class="chart-caption">显示用量最高的 {{ usage.models.length }} 项；上方汇总包含全部模型。</p></details>
    </template>
    <p v-else-if="loading" class="empty" role="status">正在读取模型用量…</p>
  </section>
</template>
<style scoped>
.token-usage { margin:0 0 8px; }
.token-usage-heading { align-items:flex-start; }
.token-period { font-size:11px; margin:8px 0 0; }
.token-range { display:flex; align-items:center; gap:10px; margin:0; flex-shrink:0; font-size:11px; color:var(--muted); }
.token-range label { margin:0; font-size:inherit; white-space:nowrap; }
.token-range select { width:120px; font-size:12px; }
.token-metrics { margin:24px 0 12px; }
.token-metrics :deep(.stat-card strong) { font-size:34px; overflow-wrap:anywhere; }
.metric-caption { margin:9px 0 0; color:var(--muted); font-size:11px; }
.token-recording-note { margin-bottom:15px; }
.token-incomplete { color:var(--warning); font-size:12px; line-height:1.8; margin:8px 0 16px; }
.token-charts { margin:22px 0 4px; }
.token-chart { min-width:0; }
.token-chart + .token-chart { padding-left:30px; border-left:1px solid var(--border); }
.token-chart h3 { font-family:var(--study-serif); font-size:20px; font-weight:600; margin:0 0 6px; }
.token-empty { padding:24px 0; margin:0; text-align:left; font-size:13px; }
.token-model-details { margin-top:16px; border-top:1px solid var(--border); padding-top:15px; }
.token-model-details summary { display:flex; align-items:center; gap:10px; font-size:13px; cursor:pointer; }
.token-model-table { overflow-x:auto; margin-top:15px; }
.token-model-table table { min-width:640px; }
.token-model-table th:not(:first-child), .token-model-table td:not(:first-child) { text-align:right; white-space:nowrap; }
.token-model-table td:first-child { min-width:160px; max-width:280px; overflow-wrap:anywhere; }
.token-model-table small { display:block; color:var(--muted); font-size:10px; margin-top:5px; }
@media(max-width:760px) {
  .token-usage-heading { flex-wrap:wrap; gap:16px; }
  .token-range { width:100%; justify-content:space-between; }
  .token-metrics :deep(.stat-card strong) { font-size:28px; }
  .token-chart + .token-chart { padding-left:0; border-left:0; margin-top:22px; }
}
</style>
