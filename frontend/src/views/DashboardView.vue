<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '../components/AppIcon.vue'
import AppModal from '../components/AppModal.vue'
import DashboardReview from '../components/dashboard/DashboardReview.vue'
import DashboardTasks from '../components/dashboard/DashboardTasks.vue'
import DashboardActivity from '../components/dashboard/DashboardActivity.vue'
import DashboardLibrary from '../components/dashboard/DashboardLibrary.vue'
import { useDashboard } from '../composables/useDashboard'

const { data, error, trendError, recordError, trendData, studyOpen, busy, record, saved, completedTasks, tasks, dateLabel, pendingTasks, load, loadTrend, toggle, openStudy, saveStudy } = useDashboard()
const metrics = computed(() => data.value ? [
  { label: '今日练习', value: data.value.stats.practiceCount, unit: '道' },
  { label: '今日错题', value: data.value.stats.wrongCount, unit: '道' },
  { label: '已完成复习', value: data.value.stats.reviewsCompleted, unit: '道' },
  { label: '本周用时', value: Math.round(data.value.stats.weeklySeconds / 60), unit: '分钟' },
] : [])
</script>

<template>
  <div class="study-overview">
    <div class="study-heading">
      <div><h1 class="sr-only">今日学习</h1><p class="study-date">{{ dateLabel || '正在读取学习记录…' }}<span v-if="data?.streak" class="study-streak">连续学习 {{ data.streak }} 天</span></p></div>
      <div class="study-actions"><button class="study-button study-button-secondary" @click="openStudy"><AppIcon name="pen" :size="18" />记录学习</button><RouterLink class="study-button study-button-primary" to="/questions/new" aria-label="录入错题"><AppIcon name="plus" :size="18" />录入错题</RouterLink></div>
    </div>
    <p v-if="error" class="error" role="alert">{{ error }}<button class="study-link" @click="load">重试</button></p>
    <p v-if="saved" class="study-save-message" role="status"><AppIcon name="check" :size="16" />学习记录已保存。<button class="study-link" aria-label="关闭保存提示" @click="saved = false">关闭</button></p>
    <div v-if="!data && !error" class="study-loading" role="status" aria-label="正在读取学习记录"><div v-for="n in 4" :key="n"></div><div class="study-loading-review"></div></div>
    <template v-if="data">
      <dl class="study-metrics"><div v-for="metric in metrics" :key="metric.label"><dt>{{ metric.label }}</dt><dd><strong>{{ metric.value }}</strong><span>{{ metric.unit }}</span></dd></div></dl>
      <DashboardReview :due-reviews="data.stats.dueReviews" />
      <div class="study-work"><DashboardTasks :tasks="tasks" :completed="completedTasks" :pending="pendingTasks" @toggle="toggle" /><DashboardActivity :days="trendData" :error="trendError" @retry="loadTrend" /></div>
      <DashboardLibrary :data="data" />
      <footer class="study-footer"><span>知序 · 公考学习</span><RouterLink to="/records" class="study-link">查看学习记录<AppIcon name="arrow-right" :size="14" /></RouterLink></footer>
    </template>
    <AppModal v-model:open="studyOpen" title="记录一次学习" :busy="busy"><form @submit.prevent="saveStudy"><h2>记录一次学习</h2><label>学习类型<select v-model="record.kind"><option value="PRACTICE">练习</option><option value="READING">阅读 / 知识整理</option></select></label><div class="form-grid"><label>练习数量<input v-model.number="record.quantity" type="number" min="0" max="10000" required></label><label>其中错题<input v-model.number="record.wrongCount" type="number" min="0" :max="record.quantity" required></label></div><label>学习用时（分钟）<input v-model.number="record.minutes" type="number" min="0" max="1440" required></label><label>备注<textarea v-model="record.note" rows="3" maxlength="1000" placeholder="本次学习内容（选填）"></textarea></label><p v-if="recordError" class="error" role="alert">{{ recordError }}</p><div class="actions"><button type="button" class="button secondary" :disabled="busy" @click="studyOpen = false">取消</button><button class="button" :disabled="busy">{{ busy ? '正在保存…' : '保存记录' }}</button></div></form></AppModal>
  </div>
</template>
