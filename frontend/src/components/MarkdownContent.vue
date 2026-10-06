<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '../lib/markdown'
import 'katex/dist/katex.min.css'

const props = defineProps<{ content: string; displayEquations?: boolean }>()
const rendered = computed(() => renderMarkdown(props.content, { displayEquations: props.displayEquations }))
</script>

<template>
  <div class="markdown-content" v-html="rendered"></div>
</template>

<style scoped>
.markdown-content { min-width:0; font-size:15px; line-height:1.9; overflow-wrap:anywhere; white-space:normal; }
.markdown-content :deep(> :first-child) { margin-top:0; }
.markdown-content :deep(> :last-child) { margin-bottom:0; }
.markdown-content :deep(h1), .markdown-content :deep(h2), .markdown-content :deep(h3),
.markdown-content :deep(h4), .markdown-content :deep(h5), .markdown-content :deep(h6) {
  display:block; line-height:1.55; font-weight:650; letter-spacing:0; margin:1.6em 0 .7em;
}
.markdown-content :deep(h1) { font-size:23px; }
.markdown-content :deep(h2) { font-size:20px; padding-bottom:10px; border-bottom:1px solid var(--border); }
.markdown-content :deep(h3) { font-size:17px; }
.markdown-content :deep(h4), .markdown-content :deep(h5), .markdown-content :deep(h6) { font-size:15px; }
.markdown-content :deep(p) { margin:.85em 0; }
.markdown-content :deep(strong) { font-weight:650; color:var(--study-ink); }
.markdown-content :deep(em) { font-style:italic; }
.markdown-content :deep(ul), .markdown-content :deep(ol) { padding-left:1.6em; margin:.9em 0; }
.markdown-content :deep(ul) { list-style:disc; }
.markdown-content :deep(ol) { list-style:decimal; }
.markdown-content :deep(ul ul) { list-style:circle; }
.markdown-content :deep(li) { padding-left:.2em; margin:.35em 0; }
.markdown-content :deep(li::marker) { color:var(--study-blue); font-weight:600; }
.markdown-content :deep(li > p) { margin:.4em 0; }
.markdown-content :deep(blockquote) { margin:1.2em 0; padding:12px 18px; background:var(--soft); border-left:3px solid var(--accent); border-radius:0 8px 8px 0; }
.markdown-content :deep(blockquote > :first-child) { margin-top:0; }
.markdown-content :deep(blockquote > :last-child) { margin-bottom:0; }
.markdown-content :deep(hr) { height:0; border:0; border-top:1px solid var(--border); margin:1.7em 0; }
.markdown-content :deep(a) { color:var(--study-blue); text-decoration:underline; text-underline-offset:3px; }
.markdown-content :deep(code) { font-family:ui-monospace,SFMono-Regular,Consolas,monospace; font-size:.88em; background:var(--soft); padding:.15em .4em; border-radius:5px; white-space:break-spaces; }
.markdown-content :deep(pre) { max-width:100%; margin:1.2em 0; padding:16px 18px; overflow:auto; background:var(--soft); border:1px solid var(--border); border-radius:9px; line-height:1.7; tab-size:2; }
.markdown-content :deep(pre code) { padding:0; background:none; border:0; border-radius:0; white-space:pre; overflow-wrap:normal; }
.markdown-content :deep(.markdown-table-scroll) { max-width:100%; margin:1.3em 0; overflow-x:auto; border:1px solid var(--border); border-radius:9px; }
.markdown-content :deep(table) { width:100%; min-width:400px; white-space:normal; line-height:1.75; font-size:14px; }
.markdown-content :deep(th), .markdown-content :deep(td) { padding:12px 16px; min-width:105px; vertical-align:top; }
.markdown-content :deep(th) { font-weight:650; color:inherit; background:var(--soft); }
.markdown-content :deep(tr:last-child td) { border-bottom:0; }
.markdown-content :deep(tbody tr:nth-child(even)) { background:color-mix(in srgb,var(--soft) 45%,var(--panel)); }
.markdown-content :deep(.math-block) { max-width:100%; overflow-x:auto; padding:14px 18px; margin:1.2em 0; background:var(--soft); border:1px solid var(--border); border-radius:9px; }
.markdown-content :deep(.math-equation) { display:block; }
.markdown-content :deep(.katex-display) { margin:.35em 0; }
.markdown-content :deep(.math-inline) { display:inline-block; max-width:100%; overflow-x:auto; overflow-y:hidden; vertical-align:middle; padding:3px 1px; }
.markdown-content :deep(.katex) { font-size:1.12em; text-indent:0; }
.markdown-content :deep(.math-fallback) { white-space:pre-wrap; }
.markdown-content :deep([tabindex="0"]:focus-visible) { outline:2px solid var(--accent); outline-offset:3px; }
@media(max-width:760px) {
  .markdown-content { font-size:14px; line-height:1.85; }
  .markdown-content :deep(h1) { font-size:21px; }
  .markdown-content :deep(h2) { font-size:18px; }
  .markdown-content :deep(h3) { font-size:16px; }
  .markdown-content :deep(th), .markdown-content :deep(td) { padding:10px 12px; }
  .markdown-content :deep(pre), .markdown-content :deep(.math-block) { padding:12px; }
}
</style>
