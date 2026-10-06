<script setup lang="ts">
withDefaults(defineProps<{ name?: string }>(), { name: 'book' })
</script>
<template>
  <svg class="study-sketch" viewBox="0 0 112 96" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    <template v-if="name === 'words'">
      <path class="sketch-wash" d="M21 23 70 17 78 66 27 72Z" />
      <path d="M21 22q24-2 49-6l8 50q-24 5-51 7Z M35 31q23 2 49 1l-1 48q-23-1-48 1Z" />
      <path d="m34 40 25-3m-24 9 15-1M46 48q12 1 25 0m-24 8 18 1m-18 7 24-1" />
      <path class="sketch-pencil" d="m88 18-4 14m9-12-4 14M16 78q6-2 13 0" />
    </template>
    <template v-else-if="name === 'folder'">
      <path class="sketch-wash" d="M17 38q18-1 36 0l7 8h33l-8 35H23Z" />
      <path d="m19 43-2-19 27-1 8 9q22-1 41 0l-2 15M19 43q19-1 34 0l6 7 34-1-10 32q-29-1-60 0Z" />
      <path d="m30 33 3-18 32 2 1 23m-25-14 15 1M29 67q20 1 40 0" />
      <path class="sketch-pencil" d="m83 14 4 7m-7-4 11 1M32 86q16-2 33-1" />
    </template>
    <template v-else-if="name === 'network'">
      <path d="M53 39 31 58m28-18 22 15M31 69l45 1" />
      <path class="sketch-wash" d="m43 16 25 2-2 25-24-2Z" />
      <path d="m42 16 27 1-2 25-26-1Zm-26 39 28-1 1 26-27 2Zm58 0 25 2-1 27-26-2Z" />
      <path d="m48 28 12 1m-36 36 14-1m42 3 10 1" />
      <path class="sketch-pencil" d="m80 19 7 5m-64 6 6-4M43 88q13-2 25 0" />
    </template>
    <template v-else-if="name === 'calendar'">
      <path class="sketch-wash" d="m23 21 63 1-1 16-61 1Z" />
      <path d="m22 23 65-2-1 58q-33 2-64 0Zm2 16q31-2 60-1M36 14l-1 16m35-15 1 15" />
      <path d="m35 50 7 1m12-1 7 1m11-2 5 1m-40 12 6-1m12 1 7-1m11 0 5 1" />
      <path class="sketch-pencil" d="m88 83 7-7m-5 1 5 6M32 86q13-1 23 0" />
    </template>
    <template v-else-if="name === 'clock'">
      <path class="sketch-wash" d="M56 18c-17-1-32 14-32 30s12 31 31 32c17 0 33-13 33-31S74 18 56 18Z" />
      <path d="M55 18c-19-1-34 15-33 32s14 30 32 30c19 1 34-14 34-31S75 18 55 18Z M55 30l1 20 16 8" />
      <path d="m53 22 1 5m-26 22 5-1m47 2 4 1m-29 21 1 4" />
      <path class="sketch-pencil" d="m17 19 7-5m64 5 7 6M35 86q21-2 39 1" />
    </template>
    <template v-else-if="name === 'chat'">
      <path class="sketch-wash" d="m20 23 67-1 1 39-45 1-19 13Z" />
      <path d="M20 24q33-2 66-1l2 38-44 1-19 14 1-14-7-1Zm11 11q23 2 43 0m-43 11 31 1" />
      <path d="m55 72 19 1 12 8-1-9 9-1-1-25" />
      <path class="sketch-pencil" d="m95 20-3 7m8-2-6 5M20 83q10-1 21 0" />
    </template>
    <template v-else>
      <path class="sketch-wash" d="M14 34q19-4 40 5v39q-21-10-39-6Z" />
      <path d="M13 31q22-5 42 4 21-11 39-8l1 45q-22-2-39 10-20-10-42-7Zm42 4 1 47M19 39q12-2 25 2m-24 8 20 3m-20 6 23 4m20-18 19-5m-18 14 15-4m-14 13 18-5" />
      <path d="m14 79 1 3q20-4 40 6 20-11 39-10l1-6" />
      <path class="sketch-pencil" d="m78 12 6-6 5 5-22 22-8 3 3-9Zm-16 15 5 6m11-21 5 5" />
      <path class="sketch-pencil" d="m27 18-2 7m9-3-5 3" />
    </template>
  </svg>
</template>
<style scoped>
.study-sketch { display:block; flex-shrink:0; color:var(--accent); }
.sketch-wash { fill:var(--soft); stroke:none; }
.sketch-pencil { stroke:var(--warning); }
</style>
