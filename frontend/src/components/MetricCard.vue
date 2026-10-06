<script setup lang="ts">
import AppIcon from './AppIcon.vue'
defineProps<{ label: string; value: number | string; unit?: string; icon?: string; tone?: string }>()
</script>
<template><div class="stat-card metric-card"><div class="metric-heading"><span>{{ label }}</span><span v-if="icon" class="metric-icon" :class="tone"><AppIcon :name="icon" /></span></div><strong>{{ value }}<small v-if="unit">{{ unit }}</small></strong><slot /></div></template>
