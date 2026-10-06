<script setup lang="ts">
export interface Reference { entityType: string; entityId: string; title: string; source?: string; sourceUrl?: string | null; sourceUnverified?: boolean }
defineProps<{ references: Reference[] }>()
const link = (ref: Reference) => ({ question: '/questions/' + ref.entityId, knowledge: '/knowledge', essay: '/materials/' + ref.entityId, current_affair: '/news/' + ref.entityId, study: '/records' } as Record<string,string>)[ref.entityType] || '/'
</script>
<template><div v-if="references.length" class="citations"><small>本次参考的个人资料</small><RouterLink v-for="(ref,index) in references" :key="ref.entityType + ref.entityId" :to="link(ref)" class="citation"><b>{{ index + 1 }}</b><span>{{ ref.title }}<small>{{ ref.source }}{{ ref.sourceUnverified ? ' · 来源待核实' : '' }}</small></span></RouterLink></div></template>
