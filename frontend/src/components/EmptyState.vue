<script setup lang="ts">
import StudySketch from './StudySketch.vue'
withDefaults(defineProps<{ title: string; description: string; icon?: string }>(), { icon: 'book' })
</script>
<template><section class="panel empty-state"><div class="empty-state-content"><h2>{{ title }}</h2><p>{{ description }}</p><div v-if="$slots.default" class="actions"><slot /></div></div><StudySketch :name="icon" class="empty-state-sketch" /></section></template>
