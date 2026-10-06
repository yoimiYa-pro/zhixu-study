<script setup lang="ts">
import AppIcon from '../AppIcon.vue'
import type { DailyTask } from '../../composables/useDashboard'
defineProps<{ tasks: DailyTask[]; completed: number; pending: Set<string> }>()
defineEmits<{ toggle: [task: DailyTask] }>()
</script>

<template>
  <section class="study-plan" aria-labelledby="plan-title">
    <div class="study-section-heading"><h2 id="plan-title">今日任务</h2><span class="study-caption">{{ completed }} / {{ tasks.length }} 已完成</span></div>
    <div class="study-plan-progress" role="progressbar" aria-label="今日任务完成情况" :aria-valuenow="completed" :aria-valuemax="tasks.length || 1" aria-valuemin="0"><span :style="{ width: (tasks.length ? completed / tasks.length * 100 : 0) + '%' }"></span></div>
    <p v-if="!tasks.length" class="study-empty">暂无今日任务。</p>
    <button v-for="task in tasks" :key="task.id" type="button" class="study-task" :class="{ 'is-complete': task.completed }" :aria-pressed="task.completed" :aria-label="task.title" :disabled="pending.has(task.id)" @click="$emit('toggle', task)">
      <span class="study-task-check"><AppIcon v-if="task.completed" name="check" :size="15" /></span>
      <span class="study-task-copy"><span>{{ task.title }}</span><small>{{ task.completed ? '已完成' : '目标 ' + task.target + (task.kind === 'NEWS' ? ' 篇' : ' 道') }}</small></span>
      <span class="study-task-indicator" aria-hidden="true"><AppIcon :name="pending.has(task.id) ? 'refresh' : 'check'" :size="16" /></span>
    </button>
  </section>
</template>
