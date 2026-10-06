<script setup lang="ts">
import { computed } from 'vue'
import type { StudyDay } from '../../composables/useDashboard'
import StudyChart from '../StudyChart.vue'
import AppIcon from '../AppIcon.vue'
import { usePreferences } from '../../stores/preferences'
const props = defineProps<{ days: StudyDay[]; error: string }>()
const preferences = usePreferences()
defineEmits<{ retry: [] }>()
const hasActivity = computed(() => props.days.some(day => day.practiceCount || day.wrongCount || day.reviewsCompleted))
const chartBorder = computed(() => preferences.dark ? '#324354' : '#e3eaf0')
const chartText = computed(() => preferences.dark ? '#afbdca' : '#64748b')
const option = computed(() => ({
  color: ['#5386b0', '#a6c9e8', '#d76540'],
  textStyle: { fontFamily: 'Study Sans, Noto Sans CJK SC, sans-serif', fontSize: 11 },
  animation: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  tooltip: { trigger: 'axis', confine: true, backgroundColor: preferences.dark ? '#1e2d3b' : '#ffffff', borderColor: chartBorder.value, textStyle: { color: chartText.value } },
  grid: { left: 28, right: 5, top: 12, bottom: 52 },
  legend: { bottom: 0, itemWidth: 9, itemHeight: 9, icon: 'roundRect', itemGap: 20, textStyle: { fontSize: 11 } },
  xAxis: { type: 'category', data: props.days.map(day => day.day.slice(5).replace('-', '.')), axisTick: { show: false }, axisLine: { lineStyle: { color: chartBorder.value } }, axisLabel: { color: chartText.value, fontSize: 10, margin: 12 } },
  yAxis: { type: 'value', min: 0, minInterval: 1, splitNumber: 3, ...(!hasActivity.value ? { max: 30 } : {}), axisLabel: { color: chartText.value, fontSize: 10 }, splitLine: { lineStyle: { color: chartBorder.value, type: 'solid' } } },
  series: [
    { name: '练习', type: 'bar', barMaxWidth: 16, barGap: '30%', itemStyle: { borderRadius: [2, 2, 0, 0] }, data: hasActivity.value ? props.days.map(day => day.practiceCount) : [] },
    { name: '复习', type: 'bar', barMaxWidth: 16, itemStyle: { borderRadius: [2, 2, 0, 0] }, data: hasActivity.value ? props.days.map(day => day.reviewsCompleted) : [] },
    { name: '错题', type: 'line', symbolSize: 4, lineStyle: { width: 1.5 }, data: hasActivity.value ? props.days.map(day => day.wrongCount) : [] },
  ],
}))
</script>

<template>
  <section class="study-activity" aria-labelledby="activity-title">
    <div class="study-section-heading"><h2 id="activity-title">最近 7 天</h2><RouterLink to="/statistics" class="study-link">查看统计<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
    <p v-if="error" class="study-empty" role="alert">{{ error }}<button class="study-link" @click="$emit('retry')">重试</button></p>
    <template v-else-if="days.length">
      <div class="study-chart-wrap"><StudyChart :option="option" label="最近七天练习、错题和复习数量" /><p v-if="!hasActivity" class="study-chart-empty">最近 7 天暂无学习记录</p></div>
      <details v-if="hasActivity" class="study-chart-data"><summary>查看趋势数据</summary><div class="table-scroll"><table><thead><tr><th>日期</th><th>练习</th><th>错题</th><th>复习</th></tr></thead><tbody><tr v-for="day in days" :key="day.day"><td>{{ day.day }}</td><td>{{ day.practiceCount }}</td><td>{{ day.wrongCount }}</td><td>{{ day.reviewsCompleted }}</td></tr></tbody></table></div></details>
    </template>
    <p v-else class="study-empty" role="status">正在读取学习趋势…</p>
  </section>
</template>
