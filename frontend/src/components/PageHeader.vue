<script setup lang="ts">
defineProps<{ title: string; description?: string; visibleTitle?: boolean }>()
</script>

<template>
  <header class="page-heading" :class="{ 'has-visible-title': visibleTitle }">
    <div>
      <h1 :class="{ 'sr-only': !visibleTitle }">{{ title }}</h1>
      <slot name="description"><p v-if="description" class="muted">{{ description }}</p></slot>
    </div>
    <div v-if="$slots.default" class="page-header-actions"><slot /></div>
  </header>
</template>
