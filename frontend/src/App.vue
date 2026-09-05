<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  BookOutlined,
  DashboardOutlined,
  HeartOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuOutlined,
  ReadOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { authState, clearSession } from './auth/session'
import { logout as logoutRequest } from './auth/auth-api'

const route = useRoute()
const router = useRouter()
const mobileMenuOpen = ref(false)

watch(() => authState.token, (token, previousToken) => {
  if (!token && previousToken && route.meta.requiresAuth) {
    void router.replace({ name: 'login', query: { redirect: route.fullPath } })
  }
})

watch(() => route.fullPath, () => {
  mobileMenuOpen.value = false
})

const marineTheme = {
  token: {
    colorPrimary: '#087a98',
    colorInfo: '#087a98',
    colorLink: '#08658a',
    colorLinkHover: '#07506f',
    colorText: '#12384a',
    colorTextSecondary: '#527180',
    colorBorder: '#cfe2e7',
    colorBgLayout: '#f4f8f7',
    borderRadius: 12,
    controlHeight: 42,
    fontFamily: 'Inter, "Microsoft YaHei", "PingFang SC", system-ui, sans-serif',
  },
  components: {
    Button: { fontWeight: 650 },
    Layout: { headerBg: 'rgba(250, 253, 252, 0.9)', headerHeight: 68, headerPadding: '0' },
    Table: { headerBg: '#f3f8f8', headerColor: '#315a6a' },
  },
}

const isAdmin = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(authState.user?.role ?? ''))
const isReaderUser = computed(() => authState.user?.role === 'USER')
const isAdminRoute = computed(() => route.path.startsWith('/admin'))
const isReaderRoute = computed(() => route.name === 'reader')
const roleLabel = computed(() => {
  const role = authState.user?.role
  return role === 'SUPER_ADMIN' ? '总管理员' : role === 'ADMIN' ? '子管理员' : '注册用户'
})
const userInitial = computed(() => authState.user?.displayName?.trim().charAt(0).toUpperCase() || '客')

const adminNavigation = [
  { to: '/admin', label: '内容概览', icon: DashboardOutlined },
  { to: '/admin/categories', label: '分类管理', icon: AppstoreOutlined },
  { to: '/admin/ebooks', label: '电子书管理', icon: BookOutlined },
  { to: '/admin/users', label: '账号管理', icon: TeamOutlined },
]

async function logout() {
  try {
    await logoutRequest()
    message.success('已退出登录')
  } catch {
    message.warning('会话已失效，已清除本地状态')
  } finally {
    clearSession()
    await router.push('/')
  }
}
</script>

<template>
  <a-config-provider :theme="marineTheme" :locale="zhCN" :auto-insert-space-in-button="false">
    <a class="skip-link" href="#main-content">跳到主要内容</a>

    <a-layout v-if="isAdminRoute" class="app-shell admin-app-shell">
      <aside class="admin-sidebar" aria-label="管理台导航">
        <RouterLink class="brand admin-brand" to="/admin" aria-label="海洋生态数字电子书管理台">
          <span class="brand-mark" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
              <path d="M2 8c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
              <path d="M2 13c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
              <path d="M2 18c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
            </svg>
          </span>
          <span><strong>海洋生态</strong><small>内容管理台</small></span>
        </RouterLink>

        <nav class="admin-nav">
          <p class="admin-nav-label">工作台</p>
          <RouterLink v-for="item in adminNavigation" :key="item.to" :to="item.to" :class="['admin-nav-link', { 'is-exact': item.to === '/admin' }]">
            <component :is="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>

        <div class="admin-sidebar-footer">
          <RouterLink class="admin-nav-link" to="/"><HomeOutlined aria-hidden="true" /><span>返回公共书库</span></RouterLink>
          <RouterLink class="admin-nav-link" to="/profile"><UserOutlined aria-hidden="true" /><span>账号资料</span></RouterLink>
          <a-popconfirm title="确定要退出登录吗？" ok-text="退出" cancel-text="取消" @confirm="logout">
            <button type="button" class="admin-nav-link admin-logout"><LogoutOutlined aria-hidden="true" /><span>退出登录</span></button>
          </a-popconfirm>
        </div>
      </aside>

      <div class="admin-workspace">
        <header class="admin-topbar">
          <button class="mobile-menu-button" type="button" aria-label="打开管理导航" @click="mobileMenuOpen = true"><MenuOutlined /></button>
          <div class="admin-topbar-context"><span class="status-dot is-online" aria-hidden="true"></span>管理服务已连接</div>
          <div class="user-summary">
            <span class="nav-user-avatar" aria-hidden="true">{{ userInitial }}</span>
            <span><strong>{{ authState.user?.displayName }}</strong><small>{{ roleLabel }}</small></span>
          </div>
        </header>
        <div id="main-content" class="admin-content"><RouterView :key="$route.path" /></div>
      </div>

      <a-drawer v-model:open="mobileMenuOpen" placement="left" width="min(320px, 88vw)" title="管理台导航">
        <nav class="admin-drawer-nav">
          <RouterLink v-for="item in adminNavigation" :key="item.to" :to="item.to" class="admin-nav-link">
            <component :is="item.icon" aria-hidden="true" />{{ item.label }}
          </RouterLink>
          <RouterLink class="admin-nav-link" to="/"><HomeOutlined aria-hidden="true" />返回公共书库</RouterLink>
          <RouterLink class="admin-nav-link" to="/profile"><UserOutlined aria-hidden="true" />账号资料</RouterLink>
          <a-popconfirm title="确定要退出登录吗？" ok-text="退出" cancel-text="取消" @confirm="logout"><a-button danger block>退出登录</a-button></a-popconfirm>
        </nav>
      </a-drawer>
    </a-layout>

    <a-layout v-else class="app-shell public-app-shell" :class="{ 'reader-app-shell': isReaderRoute }">
      <a-layout-header class="site-header">
        <div class="site-header-inner">
          <RouterLink class="brand" to="/" aria-label="海洋生态数字电子书首页">
            <span class="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <path d="M2 8c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
                <path d="M2 13c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
                <path d="M2 18c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
              </svg>
            </span>
            <span>海洋生态数字电子书</span>
          </RouterLink>

          <nav class="site-nav desktop-nav" aria-label="主导航">
            <RouterLink class="nav-link" to="/"><ReadOutlined aria-hidden="true" />书库</RouterLink>
            <RouterLink v-if="isReaderUser" class="nav-link" to="/favorites"><HeartOutlined aria-hidden="true" />我的收藏</RouterLink>
            <RouterLink v-if="isAdmin" class="nav-link" to="/admin"><SettingOutlined aria-hidden="true" />管理台</RouterLink>
            <template v-if="authState.user">
              <RouterLink class="nav-user" to="/profile" :aria-label="`个人资料：${authState.user.displayName}`">
                <span class="nav-user-avatar" aria-hidden="true">{{ userInitial }}</span>
                <span class="nav-user-copy"><strong>{{ authState.user.displayName }}</strong><small>{{ roleLabel }}</small></span>
              </RouterLink>
              <a-popconfirm title="确定要退出登录吗？" ok-text="退出" cancel-text="取消" @confirm="logout">
                <a-button class="nav-logout" type="text">退出</a-button>
              </a-popconfirm>
            </template>
            <template v-else>
              <RouterLink class="nav-link" to="/login">登录</RouterLink>
              <RouterLink class="nav-register" to="/register">免费注册</RouterLink>
            </template>
          </nav>

          <button class="mobile-menu-button public-mobile-menu" type="button" aria-label="打开主导航" @click="mobileMenuOpen = true"><MenuOutlined /></button>
        </div>
      </a-layout-header>

      <div id="main-content" class="app-main ant-layout-content"><RouterView :key="$route.path" /></div>

      <footer v-if="!isReaderRoute" class="site-footer">
        <div class="footer-body">
          <div class="footer-inner">
            <div>
              <p class="footer-brand"><span class="brand-mark" aria-hidden="true"><ReadOutlined /></span>海洋生态数字电子书</p>
              <p class="footer-about">把可靠的海洋生态知识整理成可探索、可阅读、可持续维护的数字内容。</p>
            </div>
            <nav aria-label="页脚平台导航">
              <p class="footer-title">快速入口</p>
              <ul class="footer-links">
                <li><RouterLink to="/">浏览书库</RouterLink></li>
                <li v-if="isReaderUser"><RouterLink to="/favorites">我的收藏</RouterLink></li>
                <li v-if="isAdmin"><RouterLink to="/admin">进入管理台</RouterLink></li>
              </ul>
            </nav>
            <div>
              <p class="footer-title">项目说明</p>
              <p class="footer-note">校企联合综合实训 · 内容来源与授权信息随电子书公开披露</p>
            </div>
          </div>
          <p class="footer-bottom">© 2026 海洋生态数字电子书项目组</p>
        </div>
      </footer>

      <a-drawer v-model:open="mobileMenuOpen" placement="right" width="min(320px, 88vw)" title="平台导航">
        <nav class="mobile-public-nav">
          <RouterLink class="admin-nav-link" to="/"><ReadOutlined aria-hidden="true" />浏览书库</RouterLink>
          <RouterLink v-if="isReaderUser" class="admin-nav-link" to="/favorites"><HeartOutlined aria-hidden="true" />我的收藏</RouterLink>
          <RouterLink v-if="isAdmin" class="admin-nav-link" to="/admin"><SettingOutlined aria-hidden="true" />进入管理台</RouterLink>
          <RouterLink v-if="authState.user" class="admin-nav-link" to="/profile"><UserOutlined aria-hidden="true" />账号资料</RouterLink>
          <RouterLink v-if="!authState.user" class="admin-nav-link" to="/login">登录</RouterLink>
          <RouterLink v-if="!authState.user" class="admin-nav-link" to="/register">免费注册</RouterLink>
          <a-popconfirm v-if="authState.user" title="确定要退出登录吗？" ok-text="退出" cancel-text="取消" @confirm="logout">
            <a-button danger block>退出登录</a-button>
          </a-popconfirm>
        </nav>
      </a-drawer>
    </a-layout>
  </a-config-provider>
</template>
