<script setup lang="ts">
import type { Dashboard } from '../../composables/useDashboard'
import AppIcon from '../AppIcon.vue'
import { originalLink } from '../../lib/news'
defineProps<{ data: Dashboard }>()
</script>

<template>
  <div class="study-library">
    <section class="study-news" aria-labelledby="news-title">
      <div class="study-section-heading"><h2 id="news-title">今日时政</h2><RouterLink to="/news" class="study-link">全部时政<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
      <p v-if="!data.currentAffairs.length" class="study-empty">尚未收录今日时政。<RouterLink to="/news" class="study-link">收录第一篇</RouterLink></p>
      <article v-for="news in data.currentAffairs" :key="news.id" class="study-news-entry">
        <RouterLink :to="'/news/' + news.id" class="study-news-title">{{ news.title }}</RouterLink>
        <div class="study-news-meta"><span>{{ news.source }}</span><a v-if="originalLink(news.sourceUrl)" :href="originalLink(news.sourceUrl)!" target="_blank" rel="noopener noreferrer" class="study-link">查看原文<AppIcon name="external" :size="12" /></a></div>
        <div v-if="news.tags?.length" class="study-news-tags"><span v-for="tag in news.tags" :key="tag">{{ tag }}</span></div>
      </article>
    </section>
    <section class="study-knowledge" aria-labelledby="knowledge-title">
      <div class="study-section-heading"><h2 id="knowledge-title">需要巩固</h2><RouterLink to="/knowledge" class="study-link">查看知识点<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
      <p v-if="!data.weakPoints.length" class="study-empty">完成练习或复习后，<br />在这里查看知识点掌握情况。</p>
      <div v-for="point in data.weakPoints" :key="point.id" class="study-mastery"><div><span>{{ point.name }}</span><small>{{ point.mastery }}%</small></div><progress :value="point.mastery" max="100" :aria-label="point.name + '掌握度'"></progress></div>
    </section>
    <section class="study-word" aria-labelledby="word-title">
      <div class="study-section-heading"><h2 id="word-title">今日词语</h2><RouterLink to="/idioms" class="study-link">词语库<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
      <p v-if="!data.idioms.length" class="study-empty">还没有词语积累。<RouterLink to="/idioms" class="study-link">添加第一个词语</RouterLink></p>
      <div v-for="word in data.idioms" :key="word.word" class="study-word-entry"><h3>{{ word.word }}</h3><p>{{ word.definition }}</p></div>
    </section>
  </div>
  <section class="study-materials" aria-labelledby="materials-title">
    <div class="study-section-heading"><h2 id="materials-title">申论素材</h2><RouterLink to="/materials" class="study-link">素材库<AppIcon name="arrow-right" :size="15" /></RouterLink></div>
    <p v-if="!data.materials.length" class="study-empty">暂无申论素材。<RouterLink to="/materials" class="study-link">积累第一份素材</RouterLink></p>
    <RouterLink v-for="material in data.materials" :key="material.title" to="/materials" class="study-material-entry"><AppIcon name="folder" :size="19" /><strong>{{ material.title }}</strong><span>{{ material.content }}</span><AppIcon name="arrow-right" :size="16" /></RouterLink>
  </section>
</template>
