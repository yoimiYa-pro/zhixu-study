<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ value: number; total: number; label: string }>()
const percent = computed(() => props.total > 0 ? Math.round(Math.max(0, Math.min(100, props.value / props.total * 100))) : 0)
</script>
<template><div class="ring-progress" :role="total > 0 ? 'progressbar' : 'img'" :aria-label="label" :aria-valuemin="total > 0 ? 0 : undefined" :aria-valuemax="total > 0 ? 100 : undefined" :aria-valuenow="total > 0 ? percent : undefined" :aria-valuetext="total > 0 ? `${value} / ${total}` : '暂无记录'"><svg viewBox="0 0 120 120" aria-hidden="true"><circle class="ring-track" cx="60" cy="60" r="49" /><circle class="ring-value" cx="60" cy="60" r="49" pathLength="100" :stroke-dasharray="`${percent} 100`" transform="rotate(-90 60 60)" /></svg><div class="ring-text"><strong>{{ total > 0 ? percent + '%' : '—' }}</strong><small>{{ label }}</small></div></div></template>
