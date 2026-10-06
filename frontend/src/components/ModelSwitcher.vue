<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api, errorText } from '../lib/api'
import { useAuth } from '../stores/auth'
import AppIcon from './AppIcon.vue'
import AppModal from './AppModal.vue'

interface Connection { id: string; provider: string; baseUrl: string; model: string; jsonMode: boolean; hasApiKey: boolean }
interface ModelSettings { activeModel: string; selectedModel: string | null; defaultModel: string; models: string[]; revision: number; activeConnectionId: string | null; connections: Connection[]; catalogMessage?: string }
const auth = useAuth()
const settings = ref<ModelSettings | null>(null)
const loading = ref(false), switching = ref(false), error = ref(''), message = ref('')
const managerOpen = ref(false), formOpen = ref(false), editingId = ref<string | null>(null), removingId = ref<string | null>(null)
const saving = ref(false), managerError = ref(''), managerMessage = ref(''), showKey = ref(false)
const providerInput = ref<HTMLInputElement | null>(null)
const form = reactive({ provider: '', baseUrl: '', apiKey: '', model: '', jsonMode: true })
const selectedValue = computed(() => settings.value?.activeConnectionId ? '@' + settings.value.activeConnectionId : settings.value?.selectedModel || '')
const busy = computed(() => switching.value || saving.value)
let messageTimer: ReturnType<typeof setTimeout> | undefined
function normalize(value: ModelSettings): ModelSettings { return { ...value, activeConnectionId: value.activeConnectionId || null, connections: value.connections || [] } }

async function load() {
  if (!auth.authenticated || loading.value || busy.value) return
  loading.value = true
  try { settings.value = normalize((await api.get<ModelSettings>('/ai-models', { timeout: 15000 })).data); error.value = '' }
  catch (problem) { error.value = errorText(problem) }
  finally { loading.value = false }
}
async function select(event: Event) {
  const element = event.target as HTMLSelectElement
  const value = element.value
  element.value = selectedValue.value
  await choose(value)
}
async function choose(value: string) {
  const current = settings.value
  if (!current || busy.value || value === selectedValue.value) return
  const choice = value.startsWith('@') ? { connectionId: value.slice(1) } : { model: value || null }
  switching.value = true; error.value = ''; message.value = ''; managerError.value = ''; managerMessage.value = ''
  clearTimeout(messageTimer)
  try {
    settings.value = normalize((await api.post<ModelSettings>('/ai-models', { ...choice, expectedRevision: current.revision }, { timeout: 200000 })).data)
    message.value = '连接测试通过，已切换'
    messageTimer = setTimeout(() => { message.value = '' }, 5000)
    managerMessage.value = message.value
  } catch (problem) {
    error.value = errorText(problem)
    managerError.value = error.value
    // A connection interruption can happen after saving; reread the authoritative selection.
    try { settings.value = normalize((await api.get<ModelSettings>('/ai-models')).data) } catch { /* retain last known selection */ }
  } finally { switching.value = false }
}
async function openManager() {
  managerError.value = ''; managerMessage.value = ''; managerOpen.value = true
  await load()
}
async function edit(connection?: Connection) {
  editingId.value = connection?.id || null
  Object.assign(form, { provider: connection?.provider || '', baseUrl: connection?.baseUrl || '', model: connection?.model || '', apiKey: '', jsonMode: connection?.jsonMode ?? true })
  showKey.value = false; formOpen.value = true; managerError.value = ''; managerMessage.value = ''; removingId.value = null
  await nextTick(); providerInput.value?.focus()
}
function back() { form.apiKey = ''; showKey.value = false; formOpen.value = false; managerError.value = '' }
async function save() {
  if (!settings.value || busy.value) return
  saving.value = true; managerError.value = ''; managerMessage.value = ''; error.value = ''; message.value = ''
  const payload = { provider: form.provider.trim(), baseUrl: form.baseUrl.trim(), apiKey: form.apiKey.trim() || null, model: form.model.trim(), jsonMode: form.jsonMode, expectedRevision: settings.value.revision }
  try {
    const path = '/ai-models/connections' + (editingId.value ? '/' + editingId.value : '')
    const response = await api.request<ModelSettings>({ url: path, method: editingId.value ? 'PUT' : 'POST', data: payload, timeout: 200000 })
    settings.value = normalize(response.data)
    back(); managerMessage.value = '连接测试通过，已保存并使用'
  } catch (problem) {
    managerError.value = errorText(problem)
    // Refresh the revision after conflicts or an interrupted save, without resubmitting credentials.
    try { settings.value = normalize((await api.get<ModelSettings>('/ai-models')).data) } catch { /* retain last known state */ }
  } finally { saving.value = false }
}
async function remove(id: string) {
  if (!settings.value || busy.value) return
  saving.value = true; managerError.value = ''; managerMessage.value = ''
  try {
    settings.value = normalize((await api.delete<ModelSettings>('/ai-models/connections/' + id, { data: { expectedRevision: settings.value.revision } })).data)
    removingId.value = null; managerMessage.value = '模型接入已移除'
  } catch (problem) {
    managerError.value = errorText(problem)
    try { settings.value = normalize((await api.get<ModelSettings>('/ai-models')).data) } catch { /* retain last known state */ }
  } finally { saving.value = false }
}
watch(managerOpen, open => { if (!open) { back(); removingId.value = null; managerMessage.value = '' } })
watch(() => auth.authenticated, signedIn => { if (signedIn) void load(); else { settings.value = null; managerOpen.value = false; form.apiKey = '' } }, { immediate: true })
onMounted(() => window.addEventListener('focus', load))
onUnmounted(() => { window.removeEventListener('focus', load); clearTimeout(messageTimer) })
</script>

<template>
  <div class="model-switcher" :aria-busy="busy">
    <div class="model-control">
      <label for="quick-ai-model">AI 模型</label>
      <select id="quick-ai-model" :value="selectedValue" :disabled="loading || busy || !settings" title="用于后续的 AI 对话和解析任务" @change="select">
        <option v-if="!settings" value="">{{ loading ? '正在读取模型…' : '模型列表未加载' }}</option>
        <template v-else>
          <option value="" :disabled="!settings.defaultModel">{{ settings.defaultModel || '默认模型未配置' }}（默认）</option>
          <option v-if="settings.selectedModel && !settings.models.includes(settings.selectedModel)" :value="settings.selectedModel">{{ settings.selectedModel }}（当前）</option>
          <option v-for="model in settings.models" :key="model" :value="model">{{ model }}</option>
          <optgroup v-if="settings.connections.length" label="自定义接入"><option v-for="connection in settings.connections" :key="connection.id" :value="'@' + connection.id">{{ connection.provider }} · {{ connection.model }}</option></optgroup>
        </template>
      </select>
      <button class="text-button model-refresh" :disabled="loading || busy" aria-label="刷新可用模型" title="刷新可用模型" @click="load"><AppIcon name="refresh" :size="15" /></button>
      <button class="text-button model-manage" :disabled="busy" aria-label="管理模型" title="管理模型" @click="openManager"><AppIcon name="settings" :size="16" /></button>
    </div>
    <span v-if="switching || message" class="model-status" role="status">{{ switching ? '正在验证模型连接…' : message }}</span>
    <span v-if="error" class="model-error" role="alert">{{ error }}</span>
    <AppModal v-model:open="managerOpen" title="模型接入" :busy="busy" class="model-dialog">
      <div class="model-dialog-heading"><div><h2>{{ formOpen ? (editingId ? '编辑模型接入' : '添加模型接入') : '模型接入' }}</h2><p class="muted">用于对话、内容解析、申论助手与周报。</p></div><button class="text-button model-close" :disabled="busy" aria-label="关闭模型管理" @click="managerOpen = false"><AppIcon name="close" :size="20" /></button></div>
      <template v-if="!formOpen">
        <div class="model-default"><div><span class="model-source-label">服务器默认</span><strong>{{ settings?.defaultModel || '尚未配置' }}</strong></div><span v-if="settings?.defaultModel && !settings.activeConnectionId && !settings.selectedModel" class="model-active">正在使用</span><button v-else class="text-button" :disabled="busy || !settings?.defaultModel" @click="choose('')">使用默认</button></div>
        <p v-if="settings?.catalogMessage" class="muted model-catalog-message">{{ settings.catalogMessage }}</p>
        <div class="model-list-heading"><h3>自定义接入</h3><button class="text-button model-add" :disabled="busy" @click="edit()"><AppIcon name="plus" :size="15" />添加接入</button></div>
        <p v-if="loading && !settings" class="muted" role="status">正在读取模型接入…</p>
        <p v-else-if="!settings?.connections.length" class="model-empty">添加提供商地址、密钥和模型名称，即可在顶部快捷切换。</p>
        <ul v-else class="model-connections"><li v-for="connection in settings.connections" :key="connection.id">
          <div class="model-connection-line"><div class="model-connection-name"><h4>{{ connection.provider }}</h4><span v-if="connection.id === settings.activeConnectionId" class="model-active">正在使用</span></div><div class="model-row-actions"><button class="text-button" :disabled="busy || connection.id === settings.activeConnectionId" @click="choose('@' + connection.id)">使用</button><button class="text-button" :disabled="busy" :aria-label="'编辑 ' + connection.provider" @click="edit(connection)">编辑</button><button class="text-button model-remove" :disabled="busy || connection.id === settings.activeConnectionId" :title="connection.id === settings.activeConnectionId ? '先切换到其他模型再移除' : '移除此接入'" :aria-label="'移除 ' + connection.provider" @click="removingId = connection.id"><AppIcon name="trash" :size="15" /></button></div></div>
          <p class="model-connection-model">{{ connection.model }}</p><p class="model-connection-url">{{ connection.baseUrl }}<span>密钥已保存</span></p>
          <div v-if="removingId === connection.id" class="model-remove-confirm"><span>移除此模型接入？</span><button class="text-button" :disabled="busy" @click="removingId = null">保留</button><button class="text-button model-remove" :disabled="busy" @click="remove(connection.id)">确认移除</button></div>
        </li></ul>
        <p v-if="managerMessage || switching" class="model-feedback" role="status">{{ switching ? '正在验证模型连接…' : managerMessage }}</p>
        <p v-if="managerError || (!settings && error)" class="error" role="alert">{{ managerError || error }}</p>
        <div v-if="!settings && !loading" class="actions"><button class="button secondary" @click="load">重新读取</button></div>
      </template>
      <form v-else class="model-connection-form" @submit.prevent="save">
        <label for="model-provider">提供商<input id="model-provider" ref="providerInput" v-model="form.provider" required maxlength="100" placeholder="例如：DeepSeek、硅基流动或你的服务商" :disabled="busy" autocomplete="off"></label>
        <label for="model-base-url">Base URL<input id="model-base-url" v-model="form.baseUrl" required type="url" maxlength="2048" placeholder="https://api.example.com/v1" :disabled="busy" spellcheck="false" autocomplete="off" aria-describedby="model-base-help"></label><p id="model-base-help" class="model-field-help">使用 OpenAI 兼容的 HTTPS 接口基址，不填写 /chat/completions。</p>
        <label for="model-api-key">API Key</label><p v-if="editingId" id="model-key-help" class="model-key-state">已保存，留空沿用原密钥</p><div class="password-field"><input id="model-api-key" v-model="form.apiKey" :required="!editingId" :type="showKey ? 'text' : 'password'" maxlength="4096" :placeholder="editingId ? '填写新密钥可替换原密钥' : '填写提供商的 API Key'" :disabled="busy" autocomplete="off" spellcheck="false" :aria-describedby="editingId ? 'model-key-help' : undefined"><button type="button" class="password-toggle" :aria-label="showKey ? '隐藏 API Key' : '显示 API Key'" :aria-pressed="showKey" @click="showKey = !showKey"><AppIcon :name="showKey ? 'eye-off' : 'eye'" :size="17" /></button></div>
        <label for="model-name">模型名称<input id="model-name" v-model="form.model" required maxlength="200" pattern="[A-Za-z0-9][A-Za-z0-9._:\/\-]{0,199}" placeholder="例如：deepseek-chat" :disabled="busy" spellcheck="false" autocomplete="off"></label>
        <label class="model-json-mode"><input v-model="form.jsonMode" type="checkbox" :disabled="busy"><span>启用 JSON 模式<small>接口不支持该参数时可关闭，结果仍会校验。</small></span></label>
        <p class="model-save-note muted">密钥加密保存。连接测试通过后保存并切换。</p>
        <p v-if="managerError" class="error" role="alert">{{ managerError }}</p>
        <div class="actions"><button type="button" class="button secondary" :disabled="busy" @click="back">返回</button><button class="button" :disabled="busy || loading || !settings">{{ saving ? '正在验证连接…' : '验证并使用' }}</button></div>
      </form>
    </AppModal>
  </div>
</template>

<style scoped>
.model-manage, .model-close { display: grid; place-items: center; width: 26px; height: 30px; padding: 3px; flex-shrink: 0; }
.model-dialog-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 28px; }
.model-dialog-heading h2 { margin-bottom: 8px; }
.model-dialog-heading p { margin: 0; font-size: 12px; line-height: 1.8; }
.model-default { display: flex; align-items: center; justify-content: space-between; gap: 15px; padding: 18px 20px; background: var(--sidebar); border-radius: 5px; }
.model-default > div { min-width: 0; }
.model-source-label { display: block; color: var(--muted); font-size: 11px; margin-bottom: 7px; }
.model-default strong { display: block; font-size: 14px; font-weight: 500; overflow-wrap: anywhere; }
.model-active { color: var(--study-blue); font-size: 11px; white-space: nowrap; }
.model-catalog-message { margin-top: 10px; font-size: 11px; }
.model-list-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin: 28px 0 14px; }
.model-list-heading h3 { font-family: var(--study-serif); font-size: 19px; font-weight: 500; margin: 0; }
.model-add { display: inline-flex; align-items: center; gap: 5px; color: var(--accent); font-size: 12px; }
.model-empty { padding: 20px 0; border-top: 1px solid var(--border); line-height: 1.9; font-size: 12px; color: var(--muted); }
.model-connections { list-style: none; padding: 0; margin: 0; border-top: 1px solid var(--border); }
.model-connections li { padding: 20px 0; border-bottom: 1px solid var(--border); }
.model-connection-line, .model-connection-name, .model-row-actions { display: flex; align-items: center; gap: 12px; }
.model-connection-line { justify-content: space-between; align-items: flex-start; }
.model-connection-name { min-width: 0; flex-wrap: wrap; gap: 6px 12px; }
.model-connection-name h4 { font-family: var(--study-serif); font-size: 19px; margin: 0; overflow-wrap: anywhere; }
.model-row-actions { flex-shrink: 0; gap: 14px; }
.model-row-actions button { font-size: 12px; }
.model-row-actions button:disabled { opacity: .4; }
.model-remove { color: var(--danger); }
.model-connection-model { margin: 7px 0; font-size: 12px; overflow-wrap: anywhere; }
.model-connection-url { display: flex; flex-wrap: wrap; gap: 6px 12px; font-size: 11px; color: var(--muted); margin: 0; overflow-wrap: anywhere; }
.model-connection-url span { white-space: nowrap; }
.model-remove-confirm { display: flex; flex-wrap: wrap; align-items: center; gap: 15px; margin-top: 16px; padding: 12px 14px; background: var(--soft); font-size: 12px; }
.model-remove-confirm > span { margin-right: auto; }
.model-feedback { color: var(--study-blue); font-size: 12px; line-height: 1.8; margin-top: 18px; }
.model-connection-form label { display: block; margin-bottom: 18px; }
.model-connection-form label small { display: block; margin-top: 6px; color: var(--muted); font-size: 11px; font-weight: 400; line-height: 1.7; }
.model-connection-form input { margin-top: 7px; }
.model-field-help { margin: -12px 0 18px; font-size: 11px; color: var(--muted); line-height: 1.7; }
.model-key-state { display: block; font-size: 11px; color: var(--muted); margin: 5px 0 0; }
.model-connection-form label[for="model-api-key"] { margin-bottom: 0; }
.model-connection-form .password-field { margin-bottom: 18px; }
.model-connection-form .password-toggle { top: 7px; bottom: 0; }
.model-connection-form .model-json-mode { display: flex; align-items: flex-start; gap: 10px; margin: 22px 0 16px; }
.model-json-mode input { width: 15px; margin: 3px 0 0; flex-shrink: 0; }
.model-json-mode small { margin-top: 3px; }
.model-save-note { font-size: 11px; line-height: 1.8; }
@media (max-width: 420px) { .model-dialog-heading { gap: 12px; margin-bottom: 22px; } .model-default { padding: 15px; } .model-row-actions { gap: 10px; } .model-connection-name h4 { font-size: 17px; } }
</style>
