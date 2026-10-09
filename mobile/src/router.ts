import { createRouter, createWebHashHistory } from 'vue-router'

// Bosh, Tarix, Statistika – pastki menyu bilan; fan sozlamasi, test va natija – to'liq ekran.
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@m/layouts/TabsLayout.vue'),
      children: [
        { path: '', name: 'home', component: () => import('@m/views/HomeView.vue') },
        { path: 'history', component: () => import('@m/views/HistoryView.vue') },
        { path: 'stats', component: () => import('@m/views/StatsView.vue') },
      ],
    },
    { path: '/subject/:id', component: () => import('@m/views/SubjectView.vue') },
    { path: '/test/:id', component: () => import('@m/views/TestView.vue') },
    { path: '/test/:id/result', component: () => import('@m/views/ResultView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
