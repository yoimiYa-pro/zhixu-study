<script setup lang="ts">
import AppIcon from '../AppIcon.vue'
defineProps<{ dueReviews: number }>()
</script>

<template>
  <section class="study-review" aria-labelledby="review-title">
    <img class="study-review-art" src="/images/study-paper-mountains.webp" alt="" width="2170" height="725" fetchpriority="high" />
    <div class="study-review-content">
      <h2 id="review-title">到期复习</h2>
      <p class="study-review-number"><strong>{{ dueReviews }}</strong><span>道待复习</span></p>
      <p class="study-review-description">{{ dueReviews ? '按记忆节奏，巩固已学知识。' : '今日没有到期错题，按自己的节奏学习。' }}</p>
      <RouterLink to="/reviews" class="study-button study-button-primary">{{ dueReviews ? '开始今日复习' : '查看复习安排' }}<AppIcon name="arrow-right" :size="17" /></RouterLink>
    </div>
  </section>
</template>
