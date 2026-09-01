<script setup lang="ts">
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { authState, clearSession } from './auth/session'
import { logout as logoutRequest } from './auth/auth-api'

const router = useRouter()

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
  <a class="skip-link" href="#main-content">跳到主要内容</a>

  <a-layout>
  <a-layout-header class="site-header">
    <RouterLink class="brand" to="/" aria-label="海洋生态数字电子书首页">
      <span class="brand-mark" aria-hidden="true"></span>
      <span>海洋生态数字电子书</span>
    </RouterLink>
    <nav class="site-nav" aria-label="主导航">
      <RouterLink to="/">首页</RouterLink>
      <template v-if="authState.user">
        <RouterLink to="/profile">个人资料</RouterLink>
        <RouterLink v-if="['ADMIN', 'SUPER_ADMIN'].includes(authState.user.role)" to="/admin/categories">分类管理</RouterLink>
        <RouterLink v-if="['ADMIN', 'SUPER_ADMIN'].includes(authState.user.role)" to="/admin/ebooks">电子书管理</RouterLink>
        <a-popconfirm title="确定要退出登录吗？" ok-text="退出" cancel-text="取消" @confirm="logout">
          <a-button class="nav-button" type="link">退出登录</a-button>
        </a-popconfirm>
      </template>
      <RouterLink v-else to="/login">登录</RouterLink>
    </nav>
  </a-layout-header>

  <a-layout-content id="main-content"><RouterView /></a-layout-content>
  </a-layout>
</template>
