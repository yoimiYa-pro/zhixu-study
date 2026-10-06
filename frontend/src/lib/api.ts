import axios, { type InternalAxiosRequestConfig } from 'axios'

export const api = axios.create({ baseURL: '/api', timeout: 15000, withCredentials: true })
let token = ''
let timezone = 'Asia/Shanghai'
export function setTimezone(value: string) { if (value) timezone = value }
export interface UserProfile { username: string; avatar: string }
interface Session extends UserProfile { accessToken: string; timezone: string }
let refreshPromise: Promise<Session> | null = null
export function setToken(value: string) { token = value }
export function refreshSession() {
  if (!refreshPromise) refreshPromise = api.post('/auth/refresh', {}).then(r => {
    token = r.data.accessToken
    if (r.data.timezone) setTimezone(r.data.timezone)
    return r.data as Session
  }).finally(() => { refreshPromise = null })
  return refreshPromise
}
api.interceptors.request.use(config => {
  if (token && !config.url?.startsWith('/auth/')) config.headers.Authorization = `Bearer ${token}`
  if (token && config.url === '/auth/me') config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use(r => r, async error => {
  const config = error.config as InternalAxiosRequestConfig & { retried?: boolean }
  if (error.response?.status === 401 && config && !config.retried && !config.url?.startsWith('/auth/')) {
    config.retried = true
    try { await refreshSession(); return await api(config) }
    catch { token = ''; window.dispatchEvent(new Event('session-expired')) }
  }
  return Promise.reject(error)
})
export function errorText(error: unknown): string {
  if (axios.isAxiosError(error)) return error.response?.data?.message || (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试。' : '服务暂时无法连接，请稍后重试。')
  return error instanceof Error ? error.message : '操作未完成，请重试。'
}
export function formatTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN', { timeZone: timezone, hour12: false }) : '时间未提供'
}
export const questionTypes = ['常识判断', '言语理解', '数量关系', '判断推理', '资料分析', '申论']
export const reasons = ['知识盲区', '理解错误', '审题错误', '计算错误', '方法错误', '粗心', '时间不足', '记忆错误']
export interface Question {
  id: string; content: string; options: Record<string, string>; correctAnswer: string; userAnswer: string
  explanation: string; questionType: string; difficulty: string; knowledgePoints: string[]
  mistakeReason: string | null; mistakeConfirmed: boolean; mistake: boolean; source: string; year: number | null
  region: string; nextReviewAt: string | null; createdAt: string; aiStatus: string | null
  analysis: { questionType: string; knowledgePoints: string[]; difficulty: string; mistakeReason: string; analysis: string;
    correctAnswerExplanation: string; pitfalls: string[]; solutionSteps: string[]; quickMethod: string; relatedKnowledge: string[];
    reviewSuggestions: { afterDays: number; reason: string }[] } | null
  score?: number
}
export interface KnowledgePoint { id: string; name: string; parentId: string | null; description: string; mastery: number; questionCount: number; practiceCount: number }
export interface AiTask { id: string; kind: string; referenceId: string | null; status: string; errorCode: string | null; createdAt: string; attempts: number; startedAt?: string | null; completedAt?: string | null; progressDone?: number; progressTotal?: number | null; progressLabel?: string; result?: Record<string, unknown> | null }
export interface TokenTotals { calls: number; reportedCalls: number; unreportedCalls: number; incompleteCalls: number; inputTokens: number; outputTokens: number; totalTokens: number; cachedInputTokens: number; cacheReportedCalls: number }
export interface ModelTokenUsage extends TokenTotals { providerKey: string; providerName: string; model: string; kind: 'CHAT' | 'EMBEDDING' }
export interface TokenUsage { days: number; timezone: string; startDate: string; endDate: string; totals: TokenTotals; daily: (TokenTotals & { date: string })[]; models: ModelTokenUsage[]; modelCount: number; firstRecordedAt: string | null }
export function taskLabel(status: string | null) { return ({ PENDING: '等待处理', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '需重试' } as Record<string, string>)[status || ''] || '尚未分析' }
