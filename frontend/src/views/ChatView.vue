<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import Citations, { type Reference } from '../components/Citations.vue'
import AppIcon from '../components/AppIcon.vue'
import MarkdownContent from '../components/MarkdownContent.vue'
import { api, errorText, formatTime } from '../lib/api'

interface Conversation { id: string; title: string; titleManual: boolean; questionId: string | null; createdAt: string; updatedAt: string; messageCount: number; isGenerating: boolean }
interface Message { id: string; conversationId: string; turnId: string; role: 'user' | 'assistant'; content: string; citations: Reference[]; createdAt: string; retrievalStatus?: string }
interface MessagePage { messages: Message[]; hasMore: boolean }
interface Pending { turnId: string; controller: AbortController }
const route = useRoute(), router = useRouter()
const conversations = ref<Conversation[]>([]), activeId = ref(''), messages = ref<Message[]>([])
const loading = ref(false), loadingOlder = ref(false), creating = ref(false), historyLoading = ref(false), hasMore = ref(false)
const pageError = ref(''), search = ref(''), historyOpen = ref(false), messagePane = ref<HTMLElement | null>(null), input = ref<HTMLTextAreaElement | null>(null)
const historyMedia = window.matchMedia('(max-width: 760px)')
const compactHistory = ref(historyMedia.matches), readingExpanded = ref(false), historyToggle = ref<HTMLButtonElement | null>(null)
const historyStorageKey = 'study:chat-history-expanded'
function initialHistoryExpanded() {
  try { const saved = localStorage.getItem(historyStorageKey); return saved === null ? window.innerWidth >= 1280 : saved === 'true' }
  catch { return window.innerWidth >= 1280 }
}
const historyExpanded = ref(initialHistoryExpanded())
const historyVisible = computed(() => compactHistory.value ? historyOpen.value : historyExpanded.value)
function toggleHistory() {
  if (compactHistory.value) historyOpen.value = !historyOpen.value
  else {
    historyExpanded.value = !historyExpanded.value
    try { localStorage.setItem(historyStorageKey, String(historyExpanded.value)) } catch {}
  }
}
function closeHistory() {
  if (compactHistory.value) historyOpen.value = false
  else {
    historyExpanded.value = false
    try { localStorage.setItem(historyStorageKey, 'false') } catch {}
  }
  void nextTick(() => historyToggle.value?.focus())
}
function onHistoryMediaChange(event: MediaQueryListEvent) { compactHistory.value = event.matches; historyOpen.value = false }
function onLayoutKey(event: KeyboardEvent) {
  if (event.key !== 'Escape' || dialog.value?.open) return
  if (historyOpen.value) closeHistory()
  else readingExpanded.value = false
}
const drafts = reactive<Record<string, string>>({}), errors = reactive<Record<string, string>>({}), pending = reactive<Record<string, Pending>>({})
const retries = reactive<Record<string, { turnId: string; query: string }>>({})
const active = computed(() => conversations.value.find(item => item.id === activeId.value))
const query = computed({ get: () => drafts[activeId.value || 'draft'] || '', set: value => { drafts[activeId.value || 'draft'] = value } })
const busy = computed(() => Boolean(pending[activeId.value] || active.value?.isGenerating))
const visibleConversations = computed(() => conversations.value.filter(item => item.title.toLowerCase().includes(search.value.trim().toLowerCase())))
const retry = computed(() => {
  if (busy.value) return null
  if (retries[activeId.value]) return retries[activeId.value]
  const last = messages.value.at(-1)
  return last?.role === 'user' ? { turnId: last.turnId, query: last.content } : null
})
const dialog = ref<HTMLDialogElement | null>(null), dialogTarget = ref<Conversation | null>(null), dialogMode = ref<'rename' | 'delete'>('delete')
const dialogTitle = ref(''), dialogError = ref(''), dialogBusy = ref(false)
const storageKey = 'study:last-conversation'
let initialized = false, disposed = false, epoch = 0, poll: ReturnType<typeof setInterval> | undefined, polling = false, listSequence = 0
const validId = (value: unknown): value is string => typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value)
function savedId() { try { return localStorage.getItem(storageKey) || '' } catch { return '' } }
function remember(id: string) { try { if (id) localStorage.setItem(storageKey, id); else localStorage.removeItem(storageKey) } catch {} }
function nearBottom() { const pane = messagePane.value; return !pane || pane.scrollHeight - pane.scrollTop - pane.clientHeight < 120 }
async function scroll(force = false) {
  if (!force && !nearBottom()) return
  await nextTick()
  messagePane.value?.scrollTo({ top: messagePane.value.scrollHeight, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
}
async function scrollToLatestReply() {
  await nextTick()
  const pane = messagePane.value, replies = pane?.querySelectorAll<HTMLElement>('.chat-message.assistant')
  const reply = replies?.item(replies.length - 1)
  if (pane && reply) pane.scrollTo({ top: pane.scrollTop + reply.getBoundingClientRect().top - pane.getBoundingClientRect().top - 16, behavior: 'instant' })
  else await scroll(true)
}
function merge(older: Message[], newer: Message[]) {
  const byTurn = new Map<string, Message>()
  for (const item of [...older, ...newer]) byTurn.set(item.turnId + ':' + item.role, item)
  const timestamp = (value: string) => value.replace(/(?:\.(\d+))?Z$/, (_, fraction: string = '') => '.' + fraction.padEnd(9, '0') + 'Z')
  return [...byTurn.values()].sort((a, b) => timestamp(a.createdAt).localeCompare(timestamp(b.createdAt)) || a.id.localeCompare(b.id))
}
async function refreshConversations() {
  const sequence = ++listSequence
  const { data } = await api.get<Conversation[]>('/chat/conversations')
  if (!disposed && sequence === listSequence) conversations.value = data
}
async function loadMessages(preserve = false) {
  const id = activeId.value, version = epoch, follow = nearBottom()
  if (!id) return
  if (!preserve) loading.value = true
  try {
    const { data } = await api.get<MessagePage>('/chat/conversations/' + id + '/messages')
    if (disposed || version !== epoch || activeId.value !== id) return
    messages.value = preserve ? merge(messages.value, data.messages) : data.messages
    if (!preserve) hasMore.value = data.hasMore
    if (messages.value.at(-1)?.role === 'assistant') { delete retries[id]; delete errors[id] }
    if (!preserve) { loading.value = false; await scrollToLatestReply() }
    else if (follow) await scroll(true)
  } catch (error) {
    if (disposed || version !== epoch) return
    if (axios.isAxiosError(error) && error.response?.status === 404) {
      await refreshConversations()
      await openConversation(conversations.value[0]?.id || '', 'replace')
    } else pageError.value = errorText(error)
  } finally { if (version === epoch) loading.value = false }
}
async function openConversation(id: string, navigation: 'push' | 'replace' | 'none' = 'push') {
  const changed = id !== activeId.value
  if (changed) { epoch++; activeId.value = id; messages.value = []; hasMore.value = false; loadingOlder.value = false }
  historyOpen.value = false
  pageError.value = ''
  remember(id)
  if (navigation !== 'none') await router[navigation]({ path: '/chat', query: id ? { conversation: id } : {} })
  if (id) await loadMessages(!changed)
  else loading.value = false
}
async function createConversation(questionId?: string) {
  creating.value = true; pageError.value = ''
  try {
    const { data } = await api.post<Conversation>('/chat/conversations', questionId ? { questionId } : {})
    conversations.value = [data, ...conversations.value]
    search.value = ''
    await openConversation(data.id)
    await nextTick(); input.value?.focus()
    return data.id
  } catch (error) { pageError.value = errorText(error); return null }
  finally { creating.value = false }
}
async function loadOlder() {
  if (!activeId.value || loadingOlder.value || !hasMore.value || !messages.value[0]) return
  const id = activeId.value, version = epoch, pane = messagePane.value
  const oldHeight = pane?.scrollHeight || 0, oldTop = pane?.scrollTop || 0
  loadingOlder.value = true
  try {
    const { data } = await api.get<MessagePage>('/chat/conversations/' + id + '/messages', { params: { before: messages.value[0].id } })
    if (version !== epoch || disposed) return
    messages.value = merge(data.messages, messages.value); hasMore.value = data.hasMore
    await nextTick()
    if (pane) pane.scrollTop = oldTop + pane.scrollHeight - oldHeight
  } catch (error) { if (version === epoch) pageError.value = errorText(error) }
  finally { if (version === epoch) loadingOlder.value = false }
}
async function send(value = query.value, retryTurn?: string) {
  value = value.trim()
  if (!value || busy.value || creating.value || loading.value) return
  let id = activeId.value
  if (!id) { id = await createConversation() || ''; if (!id) return; delete drafts.draft }
  const turnId = retryTurn || crypto.randomUUID(), controller = new AbortController()
  const request = { turnId, controller }
  pending[id] = request; delete errors[id]; delete retries[id]; drafts[id] = ''
  if (!messages.value.some(item => item.turnId === turnId && item.role === 'user')) {
    messages.value.push({ id: turnId, conversationId: id, turnId, role: 'user', content: value, citations: [], createdAt: new Date().toISOString() })
  }
  const conversation = conversations.value.find(item => item.id === id)
  if (conversation && conversation.messageCount === 0 && !conversation.titleManual) conversation.title = Array.from(value.replace(/\s+/g, ' ')).slice(0, 60).join('')
  await scroll(true)
  try {
    const { data } = await api.post<Message>('/chat', { query: value, turnId, conversationId: id }, { timeout: 180000, signal: controller.signal })
    if (disposed || controller.signal.aborted) return
    if (data.conversationId !== id) throw new Error('未能确认回复所属的聊天，请重试。')
    if (activeId.value === id) {
      const follow = nearBottom()
      messages.value = merge(messages.value, [data])
      if (follow) await scrollToLatestReply()
    }
    delete retries[id]; delete errors[id]
  } catch (error) {
    if (!disposed && !controller.signal.aborted && !axios.isCancel(error)) { errors[id] = errorText(error); retries[id] = { turnId, query: value } }
  } finally {
    if (pending[id]?.turnId === turnId) delete pending[id]
    if (!disposed) {
      try { await refreshConversations() } catch { /* The saved reply remains visible during a list refresh failure. */ }
      if (activeId.value === id && !conversations.value.some(item => item.id === id)) await openConversation(conversations.value[0]?.id || '', 'replace')
    }
  }
}
function onInputKey(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); void send() }
}
async function showDialog(item: Conversation, mode: 'rename' | 'delete') {
  dialogTarget.value = item; dialogMode.value = mode; dialogTitle.value = item.title; dialogError.value = ''
  await nextTick(); dialog.value?.showModal()
}
async function submitDialog() {
  const target = dialogTarget.value
  if (!target || dialogBusy.value) return
  dialogBusy.value = true; dialogError.value = ''
  try {
    if (dialogMode.value === 'rename') {
      const { data } = await api.patch<Conversation>('/chat/conversations/' + target.id, { title: dialogTitle.value.trim() })
      conversations.value = conversations.value.map(item => item.id === data.id ? data : item)
    } else {
      await api.delete('/chat/conversations/' + target.id)
      pending[target.id]?.controller.abort(); delete pending[target.id]; delete drafts[target.id]; delete errors[target.id]; delete retries[target.id]
      conversations.value = conversations.value.filter(item => item.id !== target.id)
      if (activeId.value === target.id) await openConversation(conversations.value[0]?.id || '', 'replace')
      await refreshConversations()
    }
    dialog.value?.close()
  } catch (error) { dialogError.value = errorText(error) }
  finally { dialogBusy.value = false }
}
async function reloadHistory() {
  historyLoading.value = true; pageError.value = ''
  try {
    await refreshConversations()
    if (activeId.value && !active.value) await openConversation(conversations.value[0]?.id || '', 'replace')
    else if (activeId.value) await loadMessages(true)
  } catch (error) { pageError.value = errorText(error) }
  finally { historyLoading.value = false }
}
async function pollUpdates() {
  if (disposed || polling || !initialized || document.hidden) return
  polling = true
  try {
    const id = activeId.value, previous = active.value?.updatedAt, generating = active.value?.isGenerating
    await refreshConversations()
    if (id && id === activeId.value) {
      if (!active.value) await openConversation(conversations.value[0]?.id || '', 'replace')
      else if (!pending[id] && !loading.value && (generating || previous !== active.value.updatedAt)) await loadMessages(true)
    }
  } catch { /* Retry the next poll while keeping the last readable history. */ }
  finally { polling = false }
}
watch(() => [route.query.conversation, route.query.question] as const, ([value, question], [, previousQuestion]) => {
  if (!initialized || disposed) return
  if (validId(question) && question !== previousQuestion) { void createConversation(question); return }
  if (validId(value)) { if (value !== activeId.value) void openConversation(value, 'none') }
  else if (!route.query.question) {
    const remembered = savedId()
    const fallback = conversations.value.find(item => item.id === remembered)?.id || conversations.value[0]?.id || ''
    if (fallback !== activeId.value) void openConversation(fallback, 'replace')
  }
})
onMounted(async () => {
  historyMedia.addEventListener('change', onHistoryMediaChange)
  window.addEventListener('keydown', onLayoutKey)
  historyLoading.value = true
  try {
    await refreshConversations()
    if (validId(route.query.question)) await createConversation(route.query.question)
    else {
      const remembered = savedId()
      const id = validId(route.query.conversation) ? route.query.conversation : conversations.value.find(item => item.id === remembered)?.id || conversations.value[0]?.id || ''
      await openConversation(id, 'replace')
    }
  } catch (error) { pageError.value = errorText(error) }
  finally { historyLoading.value = false; initialized = true; poll = setInterval(() => { void pollUpdates() }, 5000) }
})
onUnmounted(() => { historyMedia.removeEventListener('change', onHistoryMediaChange); window.removeEventListener('keydown', onLayoutKey); disposed = true; epoch++; clearInterval(poll); for (const item of Object.values(pending)) item.controller.abort() })
</script>

<template>
  <h1 class="sr-only">AI 学习助手</h1>
  <p v-if="pageError" class="error" role="alert">{{ pageError }} <button class="text-button" @click="reloadHistory">重新加载</button></p>
  <div class="chat-layout" :class="{ 'history-open': historyOpen, 'history-collapsed': !historyVisible, 'reading-expanded': readingExpanded }">
    <aside id="conversation-sidebar" v-show="historyVisible" class="conversation-sidebar" aria-label="聊天历史">
      <div class="conversation-sidebar-top"><button class="button new-conversation" :disabled="creating" @click="createConversation()"><AppIcon name="plus" :size="18" />{{ creating ? '正在创建…' : '新建聊天' }}</button><button class="icon-button" aria-label="收起聊天历史" title="收起聊天历史" @click="closeHistory"><AppIcon name="panel-close" :size="18" /></button></div>
      <label class="sr-only" for="conversation-search">搜索聊天历史</label>
      <div class="conversation-search"><AppIcon name="search" :size="16" /><input id="conversation-search" v-model="search" type="search" placeholder="搜索聊天历史"></div>
      <div class="conversation-list-heading"><span>聊天历史 <small>{{ conversations.length }}</small></span><button class="icon-button" :disabled="historyLoading" aria-label="刷新聊天历史" @click="reloadHistory"><AppIcon name="refresh" :size="16" /></button></div>
      <div class="conversation-list">
        <p v-if="historyLoading && !conversations.length" class="muted" role="status">正在加载聊天历史…</p>
        <p v-else-if="!visibleConversations.length" class="muted conversation-empty">{{ search ? '没有找到相关聊天' : '暂无聊天记录' }}</p>
        <div v-for="item in visibleConversations" :key="item.id" class="conversation-row" :class="{ active: item.id === activeId }">
          <button class="conversation-open" :aria-label="'打开聊天：' + item.title" :aria-current="item.id === activeId ? 'page' : undefined" :title="item.title" @click="openConversation(item.id)">
            <AppIcon name="chat" :size="16" /><span><strong>{{ item.title }}</strong><small>{{ item.messageCount }} 条消息 · {{ formatTime(item.updatedAt) }}</small></span>
            <i v-if="pending[item.id] || item.isGenerating" class="conversation-pending" role="status" aria-label="正在回答"></i>
          </button>
          <div class="conversation-tools"><button class="icon-button" :aria-label="'重命名聊天：' + item.title" @click="showDialog(item, 'rename')"><AppIcon name="pen" :size="14" /></button><button class="icon-button" :aria-label="'删除聊天：' + item.title" @click="showDialog(item, 'delete')"><AppIcon name="trash" :size="14" /></button></div>
        </div>
      </div>
      <p class="conversation-footnote"><AppIcon name="book" :size="15" />对话与历史自动保存</p>
    </aside>
    <button v-if="compactHistory && historyOpen" class="conversation-backdrop" aria-label="关闭聊天历史" @click="closeHistory"></button>
    <section class="chat-workspace" aria-label="当前聊天">
      <header class="conversation-header">
        <button ref="historyToggle" class="history-toggle" aria-label="聊天历史" :title="historyVisible ? '收起聊天历史' : '展开聊天历史'" :aria-expanded="historyVisible" aria-controls="conversation-sidebar" @click="toggleHistory"><AppIcon :name="historyVisible ? 'panel-close' : 'panel-open'" :size="18" /><span class="history-label">聊天历史</span></button>
        <h2>{{ active?.title || '新建聊天' }}</h2>
        <div class="conversation-header-actions"><RouterLink v-if="active?.questionId" :to="'/questions/' + active.questionId" class="text-button attached-question"><AppIcon name="book" :size="15" />关联错题</RouterLink><button v-if="!historyVisible" class="icon-button" aria-label="新建聊天" title="新建聊天" :disabled="creating" @click="createConversation()"><AppIcon name="plus" :size="19" /></button><button class="icon-button reading-toggle" :aria-label="readingExpanded ? '退出全屏阅读' : '全屏阅读'" :title="readingExpanded ? '退出全屏阅读（Esc）' : '全屏阅读'" :aria-pressed="readingExpanded" @click="readingExpanded = !readingExpanded"><AppIcon :name="readingExpanded ? 'minimize' : 'maximize'" :size="18" /></button></div>
      </header>
      <div ref="messagePane" class="chat-messages" :aria-busy="loading">
        <div v-if="loading" class="chat-loading muted" role="status">正在读取聊天记录…</div>
        <template v-else>
          <div v-if="hasMore" class="older-messages"><button class="text-button" :disabled="loadingOlder" @click="loadOlder">{{ loadingOlder ? '正在加载…' : '加载更早的消息' }}</button></div>
          <div v-if="!messages.length" class="chat-welcome">
            <h2>新建学习对话</h2><p class="muted">输入题目或学习问题，后续可以继续追问。</p>
            <div class="chat-suggestions"><button v-for="example in ['帮我总结今天错题','今天应该复习什么？','同比增速怎么计算？']" :key="example" class="button secondary" @click="query = example; input?.focus()">{{ example }}<AppIcon name="arrow-right" :size="15" /></button></div>
          </div>
          <article v-for="message in messages" :key="message.turnId + message.role" class="chat-message" :class="message.role">
            <div class="chat-message-heading"><span class="chat-avatar"><AppIcon :name="message.role === 'user' ? 'chat' : 'book'" :size="18" /></span><span class="eyebrow">{{ message.role === 'user' ? '我的问题' : '学习助手' }}</span></div>
            <MarkdownContent v-if="message.role === 'assistant'" :content="message.content" /><p v-else class="reading">{{ message.content }}</p>
            <template v-if="message.role === 'assistant'"><p v-if="message.retrievalStatus === 'VECTOR_UNAVAILABLE'" class="muted chat-retrieval-note">向量检索暂时不可用，本次使用实际数据库记录。</p><p v-else-if="message.retrievalStatus === 'DATABASE_ONLY'" class="muted chat-retrieval-note">本次基于实际数据库记录回答。</p><Citations :references="message.citations" /></template>
          </article>
          <div v-if="busy" class="chat-thinking muted" role="status"><span class="chat-thinking-dots" aria-hidden="true"><i></i><i></i><i></i></span>正在查询个人资料并整理回答…</div>
        </template>
      </div>
      <div class="chat-composer-area">
        <p v-if="errors[activeId]" class="error" role="alert">{{ errors[activeId] }}</p>
        <p v-if="retry" class="chat-retry muted">上一条问题还没有收到回答。<button class="text-button" @click="send(retry.query, retry.turnId)">重试这条消息</button></p>
        <form class="chat-compose" @submit.prevent="send()"><label class="sr-only" for="chat-query">学习问题</label><textarea id="chat-query" ref="input" v-model="query" :disabled="creating || loading" rows="2" maxlength="3000" placeholder="输入问题，或接着上文继续问…" @keydown="onInputKey"></textarea><button class="button" :disabled="busy || creating || loading || !query.trim()" aria-label="发送问题"><AppIcon name="arrow-up" :size="19" /><span class="sr-only">{{ busy ? '整理中…' : '发送问题' }}</span></button></form>
        <p class="compose-caption"><span>{{ busy ? '回答会自动保存，期间可以切换聊天。' : 'Enter 发送 · Shift + Enter 换行' }}</span><span>{{ query.length }}/3000</span></p>
      </div>
    </section>
  </div>
  <dialog ref="dialog" class="conversation-dialog" :aria-labelledby="'conversation-dialog-title'" @cancel="dialogBusy && $event.preventDefault()" @close="dialogTarget = null">
    <form @submit.prevent="submitDialog"><h2 id="conversation-dialog-title">{{ dialogMode === 'delete' ? '删除聊天' : '重命名聊天' }}</h2><template v-if="dialogMode === 'delete'"><p class="reading">确定删除「{{ dialogTarget?.title }}」吗？</p><p class="muted">这段聊天及其中的全部消息将被删除，无法恢复。</p></template><label v-else for="conversation-title">聊天名称<input id="conversation-title" v-model="dialogTitle" required maxlength="80" :disabled="dialogBusy"></label><p v-if="dialogError" class="error" role="alert">{{ dialogError }}</p><div class="actions"><button class="button secondary" type="button" :disabled="dialogBusy" @click="dialog?.close()">取消</button><button class="button" :class="{ 'delete-confirm': dialogMode === 'delete' }" :disabled="dialogBusy || (dialogMode === 'rename' && !dialogTitle.trim())">{{ dialogBusy ? '正在保存…' : dialogMode === 'delete' ? '确认删除' : '保存名称' }}</button></div></form>
  </dialog>
</template>

<style scoped>
.chat-layout { position:relative; display:grid; grid-template-columns:264px minmax(0,1fr); gap:0; flex:1; min-height:0; }
.chat-layout.history-collapsed { grid-template-columns:minmax(0,1fr); }
.chat-layout.reading-expanded { position:fixed; inset:0; padding:12px; z-index:30; background:var(--canvas); }
.conversation-sidebar,.chat-workspace { border:0; border-radius:0; min-width:0; min-height:0; height:100%; background:var(--panel); overflow:hidden; }
.conversation-sidebar { display:flex; flex-direction:column; padding:22px 20px 0; background:var(--canvas); border-right:1px solid var(--border); }
.conversation-sidebar-top { display:flex; align-items:center; gap:6px; margin-bottom:16px; }
.new-conversation { flex:1; min-width:0; min-height:42px; padding-inline:10px; }
.conversation-search { display:flex; align-items:center; gap:7px; padding:0 9px; background:var(--panel); border:1px solid var(--border); border-radius:5px; color:var(--muted); }
.conversation-search input { padding:9px 0; border:0; background:none; min-width:0; font-size:12px; }
.conversation-search input:focus { outline:0; }
.conversation-search:focus-within { outline:2px solid var(--accent); outline-offset:2px; }
.conversation-list-heading { display:flex; justify-content:space-between; align-items:center; margin:12px 5px 8px; font-size:12px; color:var(--muted); }
.conversation-list-heading small { margin-left:4px; font-variant-numeric:tabular-nums; }
.conversation-list { min-height:0; flex:1; overflow-y:auto; overscroll-behavior:contain; padding-bottom:12px; }
.conversation-empty { padding:10px 8px; }
.conversation-row { position:relative; margin-bottom:5px; border:1px solid transparent; border-radius:5px; }
.conversation-row:hover { background:var(--soft); }
.conversation-row.active { background:var(--study-selected); border-color:transparent; }
.conversation-open { display:flex; align-items:flex-start; gap:8px; width:100%; padding:12px 10px 11px; border:0; border-radius:5px; color:inherit; background:none; text-align:left; }
.conversation-open>.app-icon { margin-top:2px; color:var(--muted); }
.conversation-open>span { min-width:0; flex:1; }
.conversation-open strong { display:block; font-size:13px; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding-right:12px; }
.conversation-open small { display:block; font-size:11px; color:var(--muted); margin-top:6px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; padding-right:58px; }
.conversation-row.active strong,.conversation-row.active>.conversation-open>.app-icon { color:var(--study-ink); }
.conversation-tools { position:absolute; display:flex; right:5px; bottom:6px; gap:1px; }
.icon-button { display:grid; place-items:center; width:32px; height:32px; padding:0; flex-shrink:0; border:0; border-radius:5px; background:none; color:var(--muted); }
.icon-button:hover { background:var(--soft); color:var(--accent); }
.conversation-pending { width:6px; height:6px; margin:6px 0 0; border-radius:50%; background:var(--accent); flex-shrink:0; animation:thinking 1.2s infinite ease-in-out; }
.conversation-footnote { display:flex; align-items:center; gap:7px; flex-shrink:0; padding:13px 5px; border-top:1px solid var(--border); margin:0; font-size:11px; color:var(--muted); }
.chat-workspace { display:flex; flex-direction:column; }
.conversation-header { display:flex; align-items:center; gap:14px; padding:16px 28px; min-height:74px; border-bottom:1px solid var(--border); flex-shrink:0; }
.history-toggle { display:inline-flex; align-items:center; justify-content:center; gap:7px; min-height:32px; padding:5px 7px; border:0; border-radius:5px; background:none; color:var(--muted); font-size:12px; flex-shrink:0; }
.history-toggle:hover { background:var(--soft); color:var(--accent); }
.conversation-header h2 { flex:1; min-width:0; margin:0; font-family:var(--study-serif); font-size:25px; font-weight:650; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.conversation-header-actions { display:flex; align-items:center; gap:7px; flex-shrink:0; }
.attached-question { white-space:nowrap; font-size:11px; }
.chat-messages { min-height:0; flex:1; overflow-y:auto; overflow-x:hidden; overscroll-behavior:contain; padding:24px 40px; font-size:16px; }
.chat-loading { padding:25px 0; text-align:center; }
.older-messages { text-align:center; margin-bottom:16px; }
.chat-message { max-width:52em; padding:16px 0; margin:0 auto 16px; border:0; border-radius:0; }
.chat-message.user { width:fit-content; max-width:88%; margin-left:auto; margin-right:0; background:var(--study-selected); padding:15px 20px; border:0; border-radius:6px; }
.chat-message-heading { display:flex; align-items:center; gap:9px; margin-bottom:12px; }
.chat-message.user .chat-message-heading { display:none; }
.chat-message.user .reading { margin-top:0; }
.chat-message.assistant :deep(h2), .chat-message.assistant :deep(h3) { font-family:var(--study-serif); font-size:23px; color:var(--study-ink); }
.chat-message-heading .eyebrow { font-size:12px; font-weight:500; letter-spacing:0; color:var(--muted); }
.chat-avatar { display:grid; place-items:center; width:22px; height:22px; color:var(--muted); flex-shrink:0; }
.user .reading { margin-bottom:0; font-size:15px; }
.chat-message :deep(.markdown-content) { font-size:16px; line-height:1.9; }
.chat-retrieval-note { margin:20px 0 0; font-size:12px; }
.chat-thinking { display:flex; align-items:center; gap:12px; padding:14px 0; max-width:52em; margin-inline:auto; }
.chat-thinking-dots { display:inline-flex; gap:4px; }
.chat-thinking-dots i { width:5px; height:5px; border-radius:50%; background:var(--accent); animation:thinking 1.2s infinite ease-in-out; }
.chat-thinking-dots i:nth-child(2) { animation-delay:.15s; }.chat-thinking-dots i:nth-child(3) { animation-delay:.3s; }
@keyframes thinking { 0%,70%,100% { opacity:.35; } 35% { opacity:1; } }
.chat-welcome { max-width:550px; margin:0 auto; padding:clamp(20px,10vh,110px) 4px 24px; text-align:left; }
.chat-welcome h2 { font-family:var(--study-serif); font-size:33px; margin:0 0 16px; }
.chat-welcome .muted { max-width:350px; margin:0; }
.chat-suggestions { display:flex; flex-direction:column; gap:0; max-width:390px; margin:28px 0 0; }
.chat-suggestions .button { justify-content:space-between; min-height:49px; padding:12px 0; border:0; border-bottom:1px solid var(--border); border-radius:0; font-size:13px; }
.chat-composer-area { border-top:0; padding:12px 28px 15px; flex-shrink:0; background:var(--panel); }
.chat-compose { margin:0; padding:12px 13px; border:1px solid var(--border); border-radius:6px; background:var(--panel); gap:12px; align-items:flex-end; }
.chat-compose:focus-within { border-color:var(--study-blue); box-shadow:0 0 0 2px color-mix(in srgb,var(--study-blue) 10%,transparent); }
.chat-compose textarea { min-width:0; min-height:44px; max-height:min(120px,20dvh); resize:vertical; padding:2px 3px; background:none; border:0; line-height:1.6; }
.chat-compose textarea:focus { outline:0; }
.chat-compose .button { width:39px; height:39px; min-height:39px; padding:0; border-radius:5px; flex-shrink:0; }
.compose-caption { display:flex; justify-content:space-between; gap:10px; font-size:10px; color:var(--muted); margin:6px 2px 0; }
.compose-caption>span:last-child { white-space:nowrap; font-variant-numeric:tabular-nums; }
.chat-retry { margin:0 0 10px; font-size:12px; }.chat-retry .text-button { margin-left:6px; font-size:12px; }
.chat-composer-area>.error { margin:0 0 10px; max-height:90px; overflow:auto; }
.conversation-backdrop { display:none; }
.conversation-dialog { margin:auto; width:min(440px,calc(100% - 32px)); max-height:90dvh; overflow:auto; border:1px solid var(--border); border-radius:8px; padding:26px; background:var(--panel); color:inherit; }
.conversation-dialog::backdrop { background:#14243570; backdrop-filter:blur(3px); }
.conversation-dialog h2 { font-size:20px; margin:0 0 18px; }
.conversation-dialog .actions { justify-content:flex-end; margin-top:24px; }.delete-confirm { background:var(--danger); color:var(--panel); }
@media(max-width:1080px) { .chat-layout { grid-template-columns:228px minmax(0,1fr); } .history-label { display:none; } .chat-messages { padding-inline:22px; } }
@media(max-width:760px) {
  .chat-layout, .chat-layout.history-collapsed { display:block; }
  .chat-layout.reading-expanded { padding:0; }
  .reading-expanded .chat-workspace { border:0; border-radius:0; }
  .conversation-sidebar { position:absolute; inset:0 auto 0 0; z-index:9; width:min(285px,88%); box-shadow:10px 0 30px #18221d30; }
  .conversation-backdrop { display:block; position:absolute; inset:0; border:0; background:#18221d70; z-index:8; }
  .conversation-header { gap:8px; padding:7px 9px; min-height:46px; }.conversation-header h2 { font-size:19px; }
  .conversation-header-actions { gap:2px; }.attached-question { font-size:0; }
  .chat-messages { padding:14px 16px; font-size:15px; }.chat-message { padding:12px 0; }.chat-message.user { padding:12px 14px; }
  .chat-message :deep(.markdown-content) { font-size:15px; }
  .chat-composer-area { padding:8px 10px 7px; }.chat-compose { gap:7px; }
  .chat-welcome { padding:24px 0 15px; } .chat-welcome h2 { font-size:27px; }.chat-suggestions { margin-top:18px; }
}
</style>
