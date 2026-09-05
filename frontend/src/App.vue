<script setup lang="ts">
import { computed, watch } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { authState, clearSession } from './auth/session'
import { logout as logoutRequest } from './auth/auth-api'

const router = useRouter()

watch(() => authState.token, (token, previousToken) => {
  if (!token && previousToken && router.currentRoute.value.meta.requiresAuth) {
    void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})

const marineTheme = {
  token: {
    colorPrimary: '#075985',
    colorInfo: '#0e7490',
    colorLink: '#075985',
    colorLinkHover: '#0e7490',
    colorText: '#10304a',
    colorTextSecondary: '#48677a',
    colorBorder: '#a8cbd6',
    colorBgLayout: '#f4fafc',
    borderRadius: 10,
    fontFamily: 'Inter, "Microsoft YaHei", "PingFang SC", system-ui, sans-serif',
  },
  components: {
    Layout: {
      headerBg: 'rgba(244, 250, 252, 0.85)',
      headerHeight: 72,
      headerPadding: '0',
    },
  },
}

const isAdmin = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(authState.user?.role ?? ''))
const isReaderUser = computed(() => authState.user?.role === 'USER')
const roleLabel = computed(() => {
  const role = authState.user?.role
  return role === 'SUPER_ADMIN' ? '总管理员' : role === 'ADMIN' ? '子管理员' : '注册用户'
})
const userInitial = computed(() => authState.user?.displayName?.trim().charAt(0).toUpperCase() || '客')

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

    <a-layout class="app-shell">
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

          <nav class="site-nav" aria-label="主导航">
            <RouterLink class="nav-link" to="/">首页</RouterLink>
            <template v-if="authState.user">
              <RouterLink v-if="isReaderUser" class="nav-link" to="/favorites">我的收藏</RouterLink>
              <RouterLink v-if="isAdmin" class="nav-link" to="/admin/categories">分类管理</RouterLink>
              <RouterLink v-if="isAdmin" class="nav-link" to="/admin/ebooks">电子书管理</RouterLink>
              <RouterLink v-if="isAdmin" class="nav-link" to="/admin/users">用户管理</RouterLink>
              <RouterLink class="nav-user" to="/profile" :aria-label="`个人资料：${authState.user.displayName}`">
                <span class="nav-user-avatar" aria-hidden="true">{{ userInitial }}</span>
                <span class="nav-user-name">{{ authState.user.displayName }}</span>
              </RouterLink>
              <a-popconfirm title="确定要退出登录吗？" ok-text="退出" cancel-text="取消" @confirm="logout">
                <a-button class="nav-button" type="link">退出登录</a-button>
              </a-popconfirm>
            </template>
            <RouterLink v-else class="nav-link" to="/login">登录</RouterLink>
          </nav>
        </div>
      </a-layout-header>

      <div id="main-content" class="app-main ant-layout-content"><RouterView :key="$route.path" /></div>

      <footer class="site-footer">
        <svg class="footer-wave" viewBox="0 0 1440 72" preserveAspectRatio="none" aria-hidden="true">
          <path d="M0,40 C240,72 480,8 720,32 C960,56 1200,60 1440,28 L1440,72 L0,72 Z" fill="#082f49" />
        </svg>
        <div class="footer-body">
          <div class="footer-inner">
            <div>
              <p class="footer-brand">
                <span class="brand-mark" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                    <path d="M2 8c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
                    <path d="M2 13c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
                    <path d="M2 18c2.5-2 5-2 7.5 0s5 2 7.5 0 3.5-1.6 5-.8" />
                  </svg>
                </span>
                海洋生态数字电子书
              </p>
              <p class="footer-about">面向海洋科普资源的数字化管理与阅读平台，提供资源组织、检索、阅读和互动能力，让每一页知识带你走近蔚蓝生态。</p>
            </div>
            <nav aria-label="页脚平台导航">
              <p class="footer-title">平台入口</p>
              <ul class="footer-links">
                <li><RouterLink to="/">首页书库</RouterLink></li>
                <li><RouterLink to="/login">登录平台</RouterLink></li>
                <li><RouterLink to="/register">注册账号</RouterLink></li>
              </ul>
            </nav>
            <nav v-if="isAdmin" aria-label="页脚管理导航">
              <p class="footer-title">内容管理</p>
              <ul class="footer-links">
                <li><RouterLink to="/admin/categories">分类管理</RouterLink></li>
                <li><RouterLink to="/admin/ebooks">电子书管理</RouterLink></li>
                <li><RouterLink to="/admin/users">用户管理</RouterLink></li>
              </ul>
            </nav>
            <div v-else>
              <p class="footer-title">当前身份</p>
              <ul class="footer-links">
                <li v-if="authState.user"><RouterLink to="/profile">{{ authState.user.displayName }} · {{ roleLabel }}</RouterLink></li>
                <li v-else><RouterLink to="/login">访客 · 登录后可点赞收藏</RouterLink></li>
              </ul>
            </div>
          </div>
          <p class="footer-bottom">© 2026 海洋生态数字电子书项目组 · 校企联合综合实训</p>
        </div>
      </footer>
    </a-layout>
  </a-config-provider>
</template>
