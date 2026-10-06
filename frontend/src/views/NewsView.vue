<script setup lang="ts">
import PageHeader from '../components/PageHeader.vue'
import AppModal from '../components/AppModal.vue'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, errorText, formatTime, taskLabel, type AiTask } from '../lib/api'
import { newsTags, originalLink, type News, type NewsOverview, type NewsSource, type FetchResult } from '../lib/news'
import AppIcon from '../components/AppIcon.vue'
import MetricCard from '../components/MetricCard.vue'
import ProgressMeter from '../components/ProgressMeter.vue'
import StudyChart from '../components/StudyChart.vue'

const route = useRoute(), router = useRouter(), items = ref<News[]>([]), overview = ref<NewsOverview | null>(null), sources = ref<NewsSource[]>([])
const error = ref(''), sourceError = ref(''), message = ref(''), date = ref(''), tag = ref(typeof route.query.tag === 'string' ? route.query.tag : ''), source = ref(''), query = ref('')
const overviewOpen = ref(window.innerWidth >= 1280 && window.innerHeight >= 900)
const open = ref(false), busy = ref(false), loading = ref(false), fetchTask = ref<AiTask | null>(null)
const editing = ref<News | null>(null), editedTags = ref<string[]>([])
const emptyForm = () => ({ title: '', content: '', source: '', sourceUrl: '', publishTime: '', sourceUnverified: true, tags: [] as string[] })
const form = ref(emptyForm())
let timer: ReturnType<typeof setInterval> | undefined, sequence = 0, polling = false, completionKey = ''
const catalog = computed(() => overview.value?.availableTags || newsTags)
const fetchResult = computed(() => fetchTask.value?.result as unknown as FetchResult | null)
const fetching = computed(() => !!fetchTask.value && ['PENDING', 'PROCESSING'].includes(fetchTask.value.status))
const tagChart = computed(() => ({ tooltip: { trigger: 'axis' }, grid: { left: 80, right: 28, top: 8, bottom: 24 }, xAxis: { type: 'value', minInterval: 1 }, yAxis: { type: 'category', inverse: true, axisLabel: { interval: 0, fontSize: 10 }, data: overview.value?.tags.map(x => x.name) }, series: [{ type: 'bar', barMaxWidth: 18, label: { show: true, position: 'right' }, data: overview.value?.tags.map(x => x.value) }] }))
const sourceChart = computed(() => ({ tooltip: { trigger: 'item' }, legend: { bottom: 0, type: 'scroll' }, series: [{ type: 'pie', radius: ['40%', '62%'], center: ['50%', '40%'], label: { show: false }, data: overview.value?.sources }] }))

async function load() {
  const request = ++sequence
  loading.value = true
  const params = { date: date.value || undefined, tag: tag.value || undefined, source: source.value || undefined, q: query.value || undefined }
  try {
    const [rows, summary] = await Promise.all([
      route.params.id ? api.get<News>('/current-affairs/' + route.params.id) : api.get<News[]>('/current-affairs', { params }),
      api.get<NewsOverview>('/current-affairs/overview', { params: { date: date.value || undefined } }),
    ])
    if (request !== sequence) return
    items.value = Array.isArray(rows.data) ? rows.data : [rows.data]
    overview.value = summary.data
    if (!date.value) date.value = summary.data.date
    error.value = ''
  } catch (problem) { if (request === sequence) error.value = errorText(problem) }
  finally { if (request === sequence) loading.value = false }
}
async function loadSources() {
  try { sources.value = (await api.get<NewsSource[]>('/current-affairs/sources')).data; sourceError.value = '' }
  catch (problem) { sourceError.value = errorText(problem) }
}
async function poll() {
  if (polling || document.hidden) return
  polling = true
  try {
    const tasks = (await api.get<AiTask[]>('/ai-tasks')).data
    const latest = tasks.find(task => task.kind === 'NEWS_FETCH')
    if (latest && (!fetchTask.value || latest.createdAt >= fetchTask.value.createdAt)) fetchTask.value = latest
    if (fetchTask.value && !tasks.some(task => task.id === fetchTask.value!.id)) fetchTask.value = (await api.get<AiTask>('/ai-tasks/' + fetchTask.value.id)).data
    const key = (fetchTask.value?.id || '') + ':' + (fetchTask.value?.status || '') + '|' + tasks.filter(task => ['NEWS_FETCH', 'ANALYZE_NEWS'].includes(task.kind)).map(task => task.id + ':' + task.status).join('|')
    if (completionKey && key !== completionKey) await load()
    completionKey = key
  } catch { /* The existing articles stay readable during task polling outages. */ }
  finally { polling = false }
}
async function fetchNews() {
  busy.value = true; error.value = ''
  try {
    const { data } = await api.post<{ taskId: string }>('/current-affairs/fetch')
    fetchTask.value = (await api.get<AiTask>('/ai-tasks/' + data.taskId)).data
    message.value = '抓取已开始，原文保存后会继续进行 AI 整理。'
  } catch (problem) { error.value = errorText(problem) }
  finally { busy.value = false }
}
async function analyze(id: string) {
  try { await api.post('/current-affairs/' + id + '/analyze'); message.value = '已提交整理任务，结果会自动更新。'; await poll() }
  catch (problem) { error.value = errorText(problem) }
}
async function save() {
  busy.value = true
  try {
    const { tags, ...fields } = form.value
    await api.post('/current-affairs', { ...fields, sourceUrl: fields.sourceUrl || null, publishTime: fields.publishTime ? new Date(fields.publishTime).toISOString() : null, classification: tags.length ? { tags } : null })
    open.value = false; form.value = emptyForm(); tag.value = ''; source.value = ''; query.value = ''; await load()
  } catch (problem) { error.value = errorText(problem) }
  finally { busy.value = false }
}
function editTags(news: News) { editing.value = news; editedTags.value = [...(news.tags || [])]; error.value = '' }
async function saveTags() {
  if (!editing.value) return
  busy.value = true
  try { await api.patch('/current-affairs/' + editing.value.id + '/tags', { tags: editedTags.value }); editing.value = null; await load() }
  catch (problem) { error.value = errorText(problem) }
  finally { busy.value = false }
}
function selectTag(value: string) { if (route.params.id) { void router.push({ path: '/news', query: { tag: value } }); return }; tag.value = value; void load() }
function resetFilters() { tag.value = ''; source.value = ''; query.value = ''; void load() }
onMounted(() => { void load(); void loadSources(); void poll(); timer = setInterval(poll, 5000) })
onUnmounted(() => { clearInterval(timer); sequence++ })
</script>

<template>
  <PageHeader :title="route.params.id ? '时政详情' : '公考时政日报'"><div class="actions"><button class="button secondary" @click="open = true"><AppIcon name="pen" :size="16" />手动收录</button><button class="button" :disabled="busy || fetching" @click="fetchNews"><AppIcon name="download" :size="16" />{{ fetching ? '正在抓取' : '抓取来源' }}</button></div></PageHeader>
  <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="message" class="notice" role="status">{{ message }}</p>
  <template v-if="overview && !route.params.id">
    <div class="stat-grid"><MetricCard label="当日收录" :value="overview.total" unit="篇" icon="news" /><MetricCard label="已完成整理" :value="overview.analyzed" unit="篇" icon="spark" /><MetricCard label="新闻来源" :value="overview.sources.length" unit="个" icon="globe" /><MetricCard label="可直达原文" :value="overview.originals" unit="篇" icon="external" /></div>
    <div class="news-visual-toggle"><button class="text-button" :aria-expanded="overviewOpen" aria-controls="news-distributions" @click="overviewOpen = !overviewOpen"><AppIcon name="chart" :size="15" />{{ overviewOpen ? '收起主题与来源分布' : '查看主题与来源分布' }}</button></div><div v-if="overviewOpen" id="news-distributions" class="news-overview content-grid"><section class="panel"><div class="panel-heading"><h2><AppIcon name="tag" />主题标签分布</h2><small>一篇新闻可有多个标签</small></div><StudyChart v-if="overview.tags.length" :option="tagChart" label="当日时政各主题标签的文章数量" /><p v-else class="empty">收录新闻后展示主题分布。</p></section><section class="panel"><h2><AppIcon name="globe" />来源分布与整理进度</h2><StudyChart v-if="overview.sources.length" :option="sourceChart" label="当日收录时政的新闻来源分布" /><p v-else class="empty">抓取或手动收录后展示来源分布。</p><ProgressMeter :value="overview.analyzed" :total="overview.total" label="AI 整理完成" :detail="overview.analyzed + ' / ' + overview.total + ' 篇'" /></section></div>
  </template>

  <details v-if="fetchTask" class="panel fetch-progress" :open="fetching || fetchTask.status === 'FAILED'"><summary><AppIcon name="download" :size="17" /><span>最近抓取</span><span class="muted">{{ formatTime(fetchTask.createdAt) }}</span><span class="status-badge" :class="fetchTask.status.toLowerCase()">{{ taskLabel(fetchTask.status) }}</span></summary><ProgressMeter :value="fetchTask.status === 'COMPLETED' ? 1 : fetchTask.progressDone || 0" :total="fetchTask.status === 'COMPLETED' ? 1 : fetchTask.progressTotal" :indeterminate="fetchTask.status === 'PROCESSING' && !fetchTask.progressTotal" :tone="fetchTask.status === 'FAILED' ? 'failed' : ''" :label="fetchTask.progressLabel || taskLabel(fetchTask.status)" :detail="formatTime(fetchTask.createdAt)" /><p v-if="fetchTask.status === 'FAILED'" class="error">本次抓取未完成，可在处理进度中查看并重试。</p><template v-if="fetchResult"><p class="muted">取得 {{ fetchResult.received ?? fetchResult.stored }} 篇 · 新增 {{ fetchResult.stored }} 篇 · 去重 {{ fetchResult.duplicates || 0 }} 篇 · 失败来源 {{ fetchResult.failedFeeds }} 个</p><div class="source-results"><div v-for="entry in fetchResult.sources || []" :key="entry.url"><span><AppIcon :name="entry.status === 'ok' ? 'check' : 'warning'" :size="16" />{{ entry.name }}</span><strong>{{ entry.status === 'ok' ? entry.articles + ' 篇' : '抓取失败' }}</strong></div></div></template><RouterLink class="text-button" to="/tasks">查看全部处理进度</RouterLink></details>
  <form v-if="!route.params.id" class="toolbar section-gap news-toolbar" @submit.prevent="load"><input v-model="date" type="date" aria-label="日报日期" @change="load"><input v-model="query" type="search" placeholder="搜索标题、原文或来源" aria-label="搜索时政" maxlength="100"><select v-model="source" aria-label="新闻来源筛选" @change="load"><option value="">全部来源</option><option v-for="entry in overview?.sources || []" :key="entry.name" :value="entry.name">{{ entry.name }}（{{ entry.value }}）</option></select><button class="button secondary" :disabled="loading"><AppIcon name="refresh" :size="16" />刷新日报</button></form>
  <div v-if="!route.params.id" class="tag-filters" aria-label="时政主题筛选"><button :class="{ active: !tag }" :aria-pressed="!tag" @click="selectTag('')">全部主题 <span>{{ overview?.total || 0 }}</span></button><button v-for="entry in overview?.tags || []" :key="entry.name" :class="{ active: tag === entry.name }" :aria-pressed="tag === entry.name" @click="selectTag(entry.name)">{{ entry.name }} <span>{{ entry.value }}</span></button><button v-if="tag && !overview?.tags.some(x => x.name === tag)" class="active" aria-pressed="true" @click="selectTag('')">{{ tag }}</button></div>
  <RouterLink v-else class="text-button section-gap" to="/news">返回时政日报</RouterLink>
  <p v-if="loading" class="muted" role="status">正在读取时政…</p>
  <p v-else-if="!route.params.id" class="muted">当前显示 {{ items.length }} 篇{{ tag ? ' · ' + tag : '' }}{{ query ? ' · 搜索「' + query + '」' : '' }}<span v-if="items.length === 200">（单次最多 200 篇，可使用筛选）</span></p>
  <section v-if="!items.length && !loading" class="panel empty"><AppIcon name="news" :size="36" /><h2>{{ tag || source || query ? '没有符合筛选条件的时政' : '尚未收录这一天的时政' }}</h2><p>抓取已配置来源，或手动保存真实新闻原文。</p><button v-if="tag || source || query" class="text-button" @click="resetFilters">清除筛选</button></section>
  <article v-for="(news, index) in items" :key="news.id" class="panel section-gap news-article" :id="news.id">
    <div class="panel-heading"><h2>{{ index + 1 }}. {{ news.title }}</h2><span class="tag" :class="{ neutral: news.sourceUnverified }">{{ news.sourceUnverified ? '来源待核实' : '已记录来源' }}</span></div>
    <div class="news-source-line"><div><strong>{{ news.source }}</strong><p class="muted">发布：{{ formatTime(news.publishTime) }} · 收录：{{ formatTime(news.fetchTime) }}</p></div><a v-if="originalLink(news.sourceUrl)" class="button secondary original-link" :href="originalLink(news.sourceUrl)!" target="_blank" rel="noopener noreferrer" :aria-label="'查看原文：' + news.title"><AppIcon name="external" :size="16" />查看原文</a><span v-else class="muted">未提供原文链接</span></div>
    <div class="article-tags"><button v-for="label in news.tags || []" :key="label" class="tag" @click="selectTag(label)"><AppIcon name="tag" :size="12" />{{ label }}</button><span v-if="!news.tags?.length" class="tag neutral">待分类</span><button class="text-button" @click="editTags(news)">编辑标签</button><small>{{ news.tagsManual ? '手动分类' : news.analysis ? '自动分类' : '初步分类' }}</small></div>
    <template v-if="news.analysis"><p class="reading news-summary">{{ news.analysis.summary }}</p><details><summary>关键事实与考点</summary><div class="facts-grid"><div v-for="[title, values] in [['关键时间', news.analysis.keyTime], ['地点', news.analysis.keyLocation], ['人物 / 机构', news.analysis.keyPeople], ['关键数字', news.analysis.keyNumbers], ['政策名称', news.analysis.policies], ['重要表述', news.analysis.statements]]" :key="String(title)"><small>{{ title }}</small><p class="reading">{{ (values as string[]).join('；') || '原文未提供' }}</p></div></div><h3>行测 / 常识考点</h3><div v-for="(points, category) in news.analysis.examPoints" :key="category"><span class="tag">{{ category }}</span><ul class="reading"><li v-for="point in points" :key="point">{{ point }}</li></ul></div></details><details><summary>申论积累</summary><p class="reading">{{ news.analysis.essay.background }}</p><div v-for="[title, values] in [['成效', news.analysis.essay.achievements], ['问题', news.analysis.essay.problems], ['原因', news.analysis.essay.causes], ['对策', news.analysis.essay.solutions], ['金句', news.analysis.essay.quotes], ['适用主题', news.analysis.essay.themes]]" :key="String(title)"><template v-if="(values as string[]).length"><h3>{{ title }}</h3><ul class="reading"><li v-for="value in values" :key="String(value)">{{ value }}</li></ul></template></div></details></template>
    <div v-else class="news-pending"><AppIcon name="spark" :size="17" /><p class="muted">原文已保存，等待 AI 整理。</p><button class="text-button" @click="analyze(news.id)">整理这条时政</button></div>
    <details><summary>查看收录内容</summary><p class="reading">{{ news.content }}</p></details>
  </article>
  <details class="panel section-gap source-panel"><summary><AppIcon name="globe" :size="17" />抓取来源 <span class="badge">{{ sources.length }} 个已配置 · RSS / 网站</span></summary><p class="muted">各来源轮流取文；仅保留近期文章，同一原文链接不会重复入库。</p><p v-if="sourceError" class="error">{{ sourceError }} <button class="text-button" @click="loadSources">重试读取来源</button></p><p v-if="!sources.length && !sourceError" class="empty">还没有配置抓取来源。</p><div class="source-grid"><div v-for="entry in sources" :key="entry.url" class="source-card"><AppIcon :name="entry.kind === 'rss' ? 'news' : 'globe'" /><div><strong>{{ entry.name }}</strong><small>{{ entry.kind === 'rss' ? 'RSS 订阅' : '新闻网站' }}</small><a v-if="originalLink(entry.url)" :href="originalLink(entry.url)!" class="text-button" target="_blank" rel="noopener noreferrer">打开来源 <AppIcon name="external" :size="13" /></a></div></div></div></details>
  <AppModal v-model:open="open" title="收录一条真实时政" :busy="busy"><form @submit.prevent="save"><h2>收录一条真实时政</h2><label>标题<input v-model="form.title" required maxlength="500"></label><label>原文内容<textarea v-model="form.content" required rows="6" maxlength="30000"></textarea></label><label>来源名称<input v-model="form.source" required maxlength="200"></label><label>原始来源链接<input v-model="form.sourceUrl" type="url" placeholder="https://" maxlength="2000"></label><label>发布时间（已知时填写）<input v-model="form.publishTime" type="datetime-local"></label><fieldset class="tag-picker"><legend>主题标签（最多 6 个，不选则自动分类）</legend><label v-for="label in catalog" :key="label"><input v-model="form.tags" type="checkbox" :value="label" :disabled="form.tags.length >= 6 && !form.tags.includes(label)">{{ label }}</label></fieldset><label class="checkbox-label"><input v-model="form.sourceUnverified" type="checkbox">来源尚待核实</label><p v-if="error" class="error">{{ error }}</p><div class="actions"><button type="button" class="button secondary" :disabled="busy" @click="open = false">取消</button><button class="button" :disabled="busy">保存原文</button></div></form></AppModal>
  <AppModal :open="Boolean(editing)" title="编辑时政标签" :busy="busy" @update:open="!$event && (editing = null)"><form @submit.prevent="saveTags"><h2>编辑时政标签</h2><p class="muted">{{ editing?.title }}</p><fieldset class="tag-picker"><legend>主题标签（最多 6 个）</legend><label v-for="label in catalog" :key="label"><input v-model="editedTags" type="checkbox" :value="label" :disabled="editedTags.length >= 6 && !editedTags.includes(label)">{{ label }}</label></fieldset><p class="muted">手动保存后，AI 整理会保留你的分类；全部取消则标记为待分类。</p><p v-if="error" class="error">{{ error }}</p><div class="actions"><button type="button" class="button secondary" :disabled="busy" @click="editing = null">取消</button><button class="button" :disabled="busy">保存标签</button></div></form></AppModal>
</template>
