export const newsTags = ['国内时政', '国际', '经济', '科技', '教育', '民生', '法治', '生态文明', '文化', '乡村振兴', '基层治理', '数字政府']
export interface News {
  id: string; title: string; content: string; source: string; sourceUrl: string | null; publishTime: string | null; fetchTime: string; sourceUnverified: boolean; tags: string[]; tagsManual: boolean
  analysis: { summary: string; keyTime: string[]; keyLocation: string[]; keyPeople: string[]; keyNumbers: string[]; policies: string[]; statements: string[]; examPoints: Record<string, string[]>; essay: { background: string; achievements: string[]; problems: string[]; causes: string[]; solutions: string[]; quotes: string[]; themes: string[] } } | null
}
export interface Distribution { name: string; value: number }
export interface NewsOverview { date: string; total: number; analyzed: number; originals: number; verified: number; tags: Distribution[]; sources: Distribution[]; availableTags: string[] }
export interface NewsSource { name: string; url: string; kind: 'rss' | 'site' }
export interface SourceResult extends NewsSource { status: 'ok' | 'failed'; articles: number; errorCode: string | null }
export interface FetchResult { received: number; stored: number; duplicates: number; failedFeeds: number; sources: SourceResult[] }
export function originalLink(value: string | null | undefined): string | null {
  if (!value) return null
  try { const url = new URL(value); return url.protocol === 'https:' && !url.username && !url.password ? url.href : null } catch { return null }
}
