<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, errorText, type UserProfile } from '../lib/api'
import { useAuth } from '../stores/auth'
import AppModal from './AppModal.vue'
import AppIcon from './AppIcon.vue'
import UserAvatar from './UserAvatar.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const auth = useAuth(), router = useRouter()
const section = ref<'profile' | 'password'>('profile'), busy = ref(''), error = ref(''), success = ref('')
const username = ref(''), verification = ref(''), currentPassword = ref(''), newPassword = ref(''), confirmation = ref('')
const showPasswords = ref(false), fileInput = ref<HTMLInputElement | null>(null)
watch(() => props.open, open => {
  verification.value = ''; currentPassword.value = ''; newPassword.value = ''; confirmation.value = ''; showPasswords.value = false
  error.value = ''; success.value = ''
  if (open) { section.value = 'profile'; username.value = auth.username }
})
function switchSection(value: 'profile' | 'password') {
  section.value = value; error.value = ''; success.value = ''; verification.value = ''; currentPassword.value = ''; newPassword.value = ''; confirmation.value = ''; showPasswords.value = false
}
async function saveProfile() {
  busy.value = 'profile'; error.value = ''; success.value = ''
  try {
    const { data } = await api.patch<UserProfile>('/account/profile', { username: username.value.trim(), currentPassword: verification.value })
    auth.updateProfile(data); username.value = data.username; verification.value = ''; success.value = '用户名已保存，下次请使用新用户名登录。'
  } catch (cause) { error.value = errorText(cause) }
  finally { busy.value = '' }
}
async function normalizeAvatar(file: File): Promise<Blob> {
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) throw new Error('请选择 PNG、JPG 或 WebP 图片。')
  if (file.size > 2 * 1024 * 1024) throw new Error('请选择不超过 2 MB 的图片。')
  try {
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as string)
      reader.onerror = () => reject(new Error('图片无法读取，请重新选择。'))
      reader.readAsDataURL(file)
    })
    const image = new Image()
    image.src = dataUrl
    await image.decode()
    if (!image.naturalWidth || !image.naturalHeight || image.naturalWidth > 8192 || image.naturalHeight > 8192) throw new Error('图片尺寸过大，请选择不超过 8192 × 8192 的图片。')
    const canvas = document.createElement('canvas'), scale = Math.min(1, 256 / Math.max(image.naturalWidth, image.naturalHeight))
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale)); canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new Error('图片处理失败，请重试。')
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    return await new Promise((resolve, reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('图片处理失败，请重试。')), 'image/png'))
  } catch (cause) {
    if (cause instanceof DOMException) throw new Error('图片无法读取，请重新选择。')
    throw cause
  }
}
async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  if (!file) return
  busy.value = 'avatar'; error.value = ''; success.value = ''
  try {
    const form = new FormData()
    form.append('file', await normalizeAvatar(file), 'avatar.png')
    const { data } = await api.post<UserProfile>('/account/avatar', form)
    auth.updateProfile(data); success.value = '头像已更新。'
  } catch (cause) { error.value = errorText(cause) }
  finally { input.value = ''; busy.value = '' }
}
async function removeAvatar() {
  busy.value = 'avatar'; error.value = ''; success.value = ''
  try { const { data } = await api.delete<UserProfile>('/account/avatar'); auth.updateProfile(data); success.value = '已恢复默认头像。' }
  catch (cause) { error.value = errorText(cause) }
  finally { busy.value = '' }
}
async function savePassword() {
  error.value = ''; success.value = ''
  if (newPassword.value !== confirmation.value) { error.value = '两次输入的新密码不一致。'; return }
  if (new TextEncoder().encode(newPassword.value).length > 72) { error.value = '新密码不能超过 72 字节。'; return }
  busy.value = 'password'
  try {
    await api.post('/account/password', { currentPassword: currentPassword.value, newPassword: newPassword.value })
    emit('update:open', false)
    try { await auth.logout() } catch { auth.clear() }
    await router.push({ path: '/login', query: { passwordChanged: '1' } })
  } catch (cause) { error.value = errorText(cause) }
  finally { busy.value = '' }
}
</script>
<template>
  <AppModal :open="open" title="个人设置" :busy="!!busy" @update:open="emit('update:open', $event)">
    <div class="settings-heading"><div><h2>个人设置</h2><p class="muted">让知序更像你的学习空间。</p></div><button class="icon-button" aria-label="关闭个人设置" :disabled="!!busy" @click="emit('update:open', false)"><AppIcon name="close" /></button></div>
    <div class="settings-sections"><button :class="{ selected: section === 'profile' }" :aria-pressed="section === 'profile'" :disabled="!!busy" @click="switchSection('profile')">个人资料</button><button :class="{ selected: section === 'password' }" :aria-pressed="section === 'password'" :disabled="!!busy" @click="switchSection('password')">修改密码</button></div>
    <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="success" class="settings-success" role="status">{{ success }}</p>
    <section v-if="section === 'profile'" aria-label="个人资料">
      <div class="settings-avatar"><UserAvatar :src="auth.avatar" :name="auth.username" /><div><div class="avatar-actions"><button type="button" class="button secondary" :disabled="!!busy" @click="fileInput?.click()">{{ busy === 'avatar' ? '正在更新…' : '上传头像' }}</button><button v-if="auth.avatar" type="button" class="text-button" :disabled="!!busy" @click="removeAvatar">移除头像</button></div><p class="muted">PNG、JPG 或 WebP，最大 2 MB。</p><input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" aria-label="选择头像图片" hidden :disabled="!!busy" @change="uploadAvatar"></div></div>
      <form @submit.prevent="saveProfile"><label>用户名<input v-model="username" autocomplete="username" maxlength="100" required :disabled="!!busy"></label><label>验证当前密码<input v-model="verification" type="password" autocomplete="current-password" maxlength="72" required :disabled="!!busy" placeholder="修改用户名时验证身份"></label><div class="actions"><button class="button" :disabled="!!busy || username.trim() === auth.username">{{ busy === 'profile' ? '正在保存…' : '保存用户名' }}</button></div></form>
    </section>
    <form v-else aria-label="修改密码" @submit.prevent="savePassword"><p class="muted password-help">新密码至少 8 个字符。修改成功后，所有设备需重新登录。</p><label>当前密码<input v-model="currentPassword" :type="showPasswords ? 'text' : 'password'" autocomplete="current-password" maxlength="72" required :disabled="!!busy"></label><label>新密码<input v-model="newPassword" :type="showPasswords ? 'text' : 'password'" autocomplete="new-password" minlength="8" maxlength="72" required :disabled="!!busy"></label><label>确认新密码<input v-model="confirmation" :type="showPasswords ? 'text' : 'password'" autocomplete="new-password" minlength="8" maxlength="72" required :disabled="!!busy"></label><button type="button" class="text-button settings-password-toggle" :aria-pressed="showPasswords" :disabled="!!busy" @click="showPasswords = !showPasswords"><AppIcon :name="showPasswords ? 'eye-off' : 'eye'" :size="16" />{{ showPasswords ? '隐藏密码' : '显示密码' }}</button><div class="actions"><button class="button" :disabled="!!busy">{{ busy === 'password' ? '正在修改…' : '修改并重新登录' }}</button></div></form>
  </AppModal>
</template>
<style scoped>
.settings-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; }
.settings-heading h2 { margin-bottom:8px; }
.settings-heading p { margin:0; font-size:12px; }
.settings-sections { display:flex; gap:24px; border-bottom:1px solid var(--border); margin:24px 0; }
.settings-sections button { background:none; border:0; border-bottom:2px solid transparent; color:var(--muted); padding:0 0 12px; font:inherit; font-size:14px; cursor:pointer; }
.settings-sections .selected { color:var(--accent); border-bottom-color:var(--accent); }
.settings-avatar { display:flex; align-items:center; gap:20px; padding-bottom:16px; }
.settings-avatar :deep(.user-avatar) { width:68px; height:68px; font-size:28px; }
.settings-avatar p { margin:8px 0 0; font-size:11px; }
.avatar-actions { display:flex; align-items:center; flex-wrap:wrap; gap:14px; }
.avatar-actions .button { padding:8px 14px; font-size:12px; }
.settings-success { color:var(--success); font-size:13px; line-height:1.7; padding:10px 14px; background:var(--soft); border-radius:5px; }
.password-help { font-size:12px; line-height:1.8; }
.settings-password-toggle { display:inline-flex; align-items:center; gap:8px; font-size:12px; }
</style>
