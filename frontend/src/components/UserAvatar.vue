<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ src: string; name: string }>()
const initial = computed(() => Array.from(props.name.trim())[0]?.toLocaleUpperCase() || '知')
</script>
<template>
  <span class="user-avatar" aria-hidden="true"><img v-if="src" :src="src" alt=""><span v-else>{{ initial }}</span></span>
</template>
<style scoped>
.user-avatar { display:inline-flex; align-items:center; justify-content:center; flex-shrink:0; width:32px; height:32px; border-radius:50%; background:var(--soft); border:1px solid var(--border); color:var(--accent); font-size:14px; font-weight:600; overflow:hidden; }
.user-avatar img { width:100%; height:100%; object-fit:cover; }
</style>
