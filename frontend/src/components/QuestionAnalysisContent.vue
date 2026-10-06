<script setup lang="ts">
import MarkdownContent from './MarkdownContent.vue'
import { formatAnalysisText } from '../lib/analysisText'
import type { Question } from '../lib/api'

defineProps<{ analysis: NonNullable<Question['analysis']> }>()
</script>

<template>
  <div class="question-analysis-content">
    <section class="analysis-section" aria-label="题目思路">
      <h3>先看懂这道题</h3>
      <MarkdownContent :content="formatAnalysisText(analysis.analysis)" />
    </section>
    <section class="analysis-section" aria-label="解题步骤">
      <h3>一步步来</h3>
      <ol class="solution-steps">
        <li v-for="(step, index) in analysis.solutionSteps" :key="index">
          <MarkdownContent :content="formatAnalysisText(step)" />
        </li>
      </ol>
    </section>
    <section class="analysis-section" aria-label="答案核对">
      <h3>答案核对</h3>
      <MarkdownContent :content="formatAnalysisText(analysis.correctAnswerExplanation)" />
    </section>
    <section class="analysis-section" aria-label="快速方法">
      <h3>考场上怎么做更快</h3>
      <MarkdownContent :content="formatAnalysisText(analysis.quickMethod)" />
    </section>
    <section v-if="analysis.pitfalls.length" class="analysis-section" aria-label="易错点">
      <h3>容易踩的坑</h3>
      <ul class="analysis-pitfalls">
        <li v-for="(pitfall, index) in analysis.pitfalls" :key="index">
          <MarkdownContent :content="formatAnalysisText(pitfall)" />
        </li>
      </ul>
    </section>
    <details class="analysis-notes">
      <summary>知识点与复习建议</summary>
      <p class="muted">AI 建议错因：{{ analysis.mistakeReason }} · 题型：{{ analysis.questionType }} · 难度：{{ analysis.difficulty }}</p>
      <p v-if="analysis.relatedKnowledge.length" class="muted">相关知识：{{ analysis.relatedKnowledge.join('、') }}</p>
      <p v-for="review in analysis.reviewSuggestions" :key="review.afterDays" class="muted">建议 {{ review.afterDays }} 天后巩固：{{ review.reason }}</p>
    </details>
  </div>
</template>

<style scoped>
.question-analysis-content { min-width:0; }
.analysis-section + .analysis-section { margin-top:30px; padding-top:24px; border-top:1px solid var(--border); }
.analysis-section > h3 { margin:0 0 14px; font-size:18px; font-family:var(--study-serif); }
.solution-steps { list-style:decimal; padding-left:30px; margin:0; }
.solution-steps > li { padding:12px 0 16px 10px; }
.solution-steps > li + li { border-top:1px solid var(--border); }
.solution-steps > li::marker { color:var(--study-blue); font-size:18px; font-weight:650; }
.analysis-pitfalls { list-style:disc; margin:0; padding-left:23px; }
.analysis-pitfalls > li { padding:6px 0 6px 5px; }
.analysis-pitfalls > li::marker { color:var(--study-blue); }
.analysis-notes { margin-top:26px; }
@media(max-width:760px) {
  .analysis-section + .analysis-section { margin-top:24px; padding-top:20px; }
  .analysis-section > h3 { font-size:17px; }
  .solution-steps { padding-left:23px; }
  .solution-steps > li { padding-left:5px; }
}
</style>
