import { createRouter, createWebHistory } from 'vue-router'
import { authState, refreshUser } from '../auth/session'
import { getCurrentUser } from '../auth/auth-api'
import { message } from 'ant-design-vue'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import ProfileView from '../views/ProfileView.vue'
import RegisterView from '../views/RegisterView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    requiresContentAdmin?: boolean
    requiresSuperAdmin?: boolean
    requiresReaderUser?: boolean
    guestOnly?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0 }
  },
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/login', name: 'login', component: LoginView, meta: { guestOnly: true } },
    { path: '/register', name: 'register', component: RegisterView, meta: { guestOnly: true } },
    { path: '/profile', name: 'profile', component: ProfileView, meta: { requiresAuth: true } },
    {
      path: '/favorites',
      name: 'favorites',
      component: () => import('../views/FavoritesView.vue'),
      meta: { requiresAuth: true, requiresReaderUser: true },
    },
    {
      path: '/admin',
      name: 'admin-dashboard',
      component: () => import('../views/AdminDashboardView.vue'),
      meta: { requiresAuth: true, requiresContentAdmin: true },
    },
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
      path: '/admin/users',
      name: 'user-management',
      component: () => import('../views/UserManagementView.vue'),
      meta: { requiresAuth: true, requiresContentAdmin: true },
    },
    {
      path: '/ebooks/:ebookId/read',
      name: 'reader',
      component: () => import('../views/ReaderView.vue'),
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('../views/NotFoundView.vue') },
  ],
})

router.beforeEach(async (to, from) => {
  if (authState.token && (to.meta.requiresAuth || to.meta.guestOnly || !from.matched.length)) {
    const token = authState.token
    try {
      const user = await getCurrentUser()
      if (token === authState.token) refreshUser(user)
    } catch (error) {
      if (authState.token && to.meta.requiresAuth) {
        message.error(error instanceof Error ? error.message : '无法验证登录状态，请重试。')
        return false
      }
    }
  }
  if (to.meta.requiresAuth && !authState.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && authState.token) {
    return { name: 'home' }
  }
  if (to.meta.requiresContentAdmin && !['ADMIN', 'SUPER_ADMIN'].includes(authState.user?.role ?? 'USER')) {
    return { name: 'home' }
  }
  if (to.meta.requiresReaderUser && authState.user?.role !== 'USER') {
    return { name: 'home' }
  }
  return true
})

export default router
