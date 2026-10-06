import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/', component: () => import('../views/DashboardView.vue') },
    { path: '/questions', component: () => import('../views/QuestionsView.vue') },
    { path: '/questions/new', component: () => import('../views/QuestionFormView.vue') },
    { path: '/questions/:id/edit', component: () => import('../views/QuestionFormView.vue') },
    { path: '/questions/:id', component: () => import('../views/QuestionDetailView.vue') },
    { path: '/reviews', component: () => import('../views/ReviewsView.vue') },
    { path: '/knowledge', component: () => import('../views/KnowledgeView.vue') },
    { path: '/news', component: () => import('../views/NewsView.vue') },
    { path: '/news/:id', component: () => import('../views/NewsView.vue') },
    { path: '/idioms', component: () => import('../views/IdiomsView.vue') },
    { path: '/materials', component: () => import('../views/MaterialsView.vue') },
    { path: '/materials/:id', component: () => import('../views/MaterialsView.vue') },
    { path: '/statistics', component: () => import('../views/StatisticsView.vue') },
    { path: '/chat', component: () => import('../views/ChatView.vue') },
    { path: '/essay-assistant', component: () => import('../views/EssayAssistantView.vue') },
    { path: '/records', component: () => import('../views/RecordsView.vue') },
    { path: '/weekly', component: () => import('../views/WeeklyView.vue') },
    { path: '/tasks', component: () => import('../views/TasksView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
router.beforeEach(async to => {
  const auth = useAuth()
  if (!auth.initialized) await auth.restore()
  if (!auth.authenticated && to.path !== '/login') return '/login'
  if (auth.authenticated && to.path === '/login') return '/'
})
export default router
