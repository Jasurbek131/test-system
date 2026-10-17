import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    admin?: boolean
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true, title: 'Kirish' } },
    {
      path: '/register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true, title: "Ro'yxatdan o'tish" },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@/views/SubjectsView.vue'), meta: { title: 'Fanlar' } },
        { path: 'test/new', component: () => import('@/views/NewTestView.vue'), meta: { title: 'Yangi test' } },
        { path: 'block/new', component: () => import('@/views/BlockTestView.vue'), meta: { title: 'Blok test' } },
        { path: 'test/:id', component: () => import('@/views/TestView.vue'), meta: { title: 'Test' } },
        { path: 'test/:id/result', component: () => import('@/views/ResultView.vue'), meta: { title: 'Natija' } },
        { path: 'history', component: () => import('@/views/HistoryView.vue'), meta: { title: 'Tarix' } },
        { path: 'stats', component: () => import('@/views/StatsView.vue'), meta: { title: 'Statistika' } },
        {
          path: 'achievements',
          component: () => import('@/views/AchievementsView.vue'),
          meta: { title: 'Yutuqlar' },
        },
        { path: 'mistakes', component: () => import('@/views/MistakesView.vue'), meta: { title: 'Xatolar daftari' } },
        {
          path: 'admin',
          meta: { admin: true },
          children: [
            { path: '', redirect: '/admin/subjects' },
            {
              path: 'subjects',
              component: () => import('@/views/admin/SubjectsAdmin.vue'),
              meta: { admin: true, title: 'Fanlar boshqaruvi' },
            },
            {
              path: 'topics',
              component: () => import('@/views/admin/TopicsAdmin.vue'),
              meta: { admin: true, title: 'Mavzular boshqaruvi' },
            },
            {
              path: 'questions',
              component: () => import('@/views/admin/QuestionsAdmin.vue'),
              meta: { admin: true, title: 'Savollar boshqaruvi' },
            },
            {
              path: 'import',
              component: () => import('@/views/admin/ImportAdmin.vue'),
              meta: { admin: true, title: 'Import' },
            },
          ],
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined }
  }
  if (to.meta.public && auth.isLoggedIn) return '/'
  if (to.meta.admin && !auth.isAdmin) return '/'
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · Test tizimi` : 'Test tizimi'
})

export default router
