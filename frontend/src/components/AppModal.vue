<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
const props = defineProps<{ open: boolean; title: string; busy?: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const dialog = ref<HTMLDialogElement | null>(null)
let previouslyFocused: HTMLElement | null = null
watch(() => props.open, async open => {
  await nextTick()
  if (open && !dialog.value?.open) {
    previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialog.value?.showModal()
  } else if (!open && dialog.value?.open) dialog.value.close()
}, { immediate: true })
function close() { emit('update:open', false); previouslyFocused?.focus() }
function onBackdrop(event: MouseEvent) {
  if (props.busy || event.target !== dialog.value) return
  const box = dialog.value!.getBoundingClientRect()
  if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) dialog.value?.close()
}
onBeforeUnmount(() => dialog.value?.close())
</script>
<template><dialog ref="dialog" class="modal" :aria-label="title" @close="close" @cancel="busy && $event.preventDefault()" @click="onBackdrop"><slot v-if="open" /></dialog></template>
