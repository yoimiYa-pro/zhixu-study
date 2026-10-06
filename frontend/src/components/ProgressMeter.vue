<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ value: number; total?: number | null; label: string; indeterminate?: boolean; tone?: string; detail?: string }>()
const maximum = computed(() => props.total && props.total > 0 ? props.total : 100)
const amount = computed(() => props.indeterminate ? undefined : Math.max(0, Math.min(props.value, maximum.value)))
const percent = computed(() => props.total && props.total > 0 ? Math.round(Math.min(100, props.value / props.total * 100)) : null)
</script>
<template><div class="progress-meter" :class="tone"><div class="progress-caption"><span>{{ label }}</span><strong v-if="!indeterminate && percent !== null">{{ percent }}%</strong></div><progress :value="amount" :max="maximum" :aria-label="label" /><small v-if="detail">{{ detail }}</small></div></template>
