import { computed, onMounted, ref } from 'vue'
import { api, errorText, type KnowledgePoint } from '../lib/api'

export interface DailyTask { id: string; kind: string; title: string; target: number; completed: boolean }
export interface StudyDay { day: string; practiceCount: number; wrongCount: number; reviewsCompleted: number }
export interface Dashboard {
  date: string; timezone: string; streak: number
  stats: { practiceCount: number; wrongCount: number; reviewsCompleted: number; dueReviews: number; weeklySeconds: number }
  tasks: DailyTask[]
  currentAffairs: { id: string; title: string; source: string; sourceUrl: string | null; tags: string[] }[]
  idioms: { word: string; definition: string }[]
  materials: { title: string; content: string }[]
  weakPoints: KnowledgePoint[]
}

export function useDashboard() {
  const data = ref<Dashboard | null>(null), error = ref(''), trendError = ref(''), recordError = ref('')
  const trendData = ref<StudyDay[]>([]), studyOpen = ref(false), busy = ref(false)
  const pendingTasks = ref(new Set<string>()), saved = ref(false)
  const record = ref({ kind: 'PRACTICE', quantity: 0, wrongCount: 0, minutes: 0, note: '' })
  const completedTasks = computed(() => data.value?.tasks.filter(task => task.completed).length || 0)
  const tasks = computed(() => [...(data.value?.tasks || [])].sort((a, b) => {
    const order: Record<string, number> = { PRACTICE: 0, REVIEW: 1, NEWS: 2 }
    return (order[a.kind] ?? 3) - (order[b.kind] ?? 3)
  }))
  // The API date is a calendar day, so formatting must not shift it into another timezone.
  const dateLabel = computed(() => data.value ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long', timeZone: 'UTC' }).formatToParts(new Date(data.value.date + 'T00:00:00Z')).map(part => (part.type === 'weekday' ? '　' : '') + part.value).join('') : '')
  async function load() {
    try { data.value = (await api.get<Dashboard>('/dashboard')).data; error.value = '' }
    catch (problem) { error.value = errorText(problem) }
  }
  async function loadTrend() {
    try { trendData.value = (await api.get('/statistics', { params: { days: 7 } })).data.trend; trendError.value = '' }
    catch (problem) { trendError.value = errorText(problem) }
  }
  async function toggle(task: DailyTask) {
    if (pendingTasks.value.has(task.id)) return
    pendingTasks.value.add(task.id)
    try {
      const { data: updated } = await api.patch<DailyTask>(`/daily-tasks/${task.id}`, { completed: !task.completed })
      if (data.value) data.value.tasks = data.value.tasks.map(row => row.id === task.id ? updated : row)
      error.value = ''
    } catch (problem) { error.value = errorText(problem) }
    finally { pendingTasks.value.delete(task.id) }
  }
  function openStudy() { recordError.value = ''; saved.value = false; studyOpen.value = true }
  async function saveStudy() {
    if (busy.value) return
    recordError.value = ''; busy.value = true
    try {
      await api.post('/study-records', { ...record.value, timeSpent: Math.round(record.value.minutes * 60) })
      studyOpen.value = false; saved.value = true
      record.value = { kind: 'PRACTICE', quantity: 0, wrongCount: 0, minutes: 0, note: '' }
      await Promise.all([load(), loadTrend()])
    } catch (problem) { recordError.value = errorText(problem) }
    finally { busy.value = false }
  }
  onMounted(() => { void load(); void loadTrend() })
  return { data, error, trendError, recordError, trendData, studyOpen, busy, record, saved, completedTasks, tasks, dateLabel, pendingTasks, load, loadTrend, toggle, openStudy, saveStudy }
}
