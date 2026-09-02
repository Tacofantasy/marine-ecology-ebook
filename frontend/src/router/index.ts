import { createRouter, createWebHistory } from 'vue-router'
import { authState } from '../auth/session'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import ProfileView from '../views/ProfileView.vue'
import RegisterView from '../views/RegisterView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresContentAdmin?: boolean
    guestOnly?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true } },
    { path: '/profile', name: 'profile', component: ProfileView, meta: { requiresAuth: true } },
    {
      path: '/admin/categories',
      name: 'category-management',
      component: () => import('../views/CategoryManagementView.vue'),
      meta: { requiresAuth: true, requiresContentAdmin: true },
    },
    {
      path: '/admin/ebooks',
      name: 'ebook-management',
      component: () => import('../views/EbookManagementView.vue'),
      meta: { requiresAuth: true, requiresContentAdmin: true },
    },
    {
      path: '/admin/ebooks/:ebookId/chapters',
      name: 'chapter-management',
      component: () => import('../views/ChapterManagementView.vue'),
      meta: { requiresAuth: true, requiresContentAdmin: true },
    },
    {
      path: '/ebooks/:ebookId/read',
      name: 'reader',
      component: () => import('../views/ReaderView.vue'),
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !authState.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && authState.token) {
    return { name: 'home' }
  }
  if (to.meta.requiresContentAdmin && !['ADMIN', 'SUPER_ADMIN'].includes(authState.user?.role ?? 'USER')) {
    return { name: 'home' }
  }
  return true
})

export default router
