<script setup lang="ts">
import { useRouter } from 'vue-router'
import { authState, clearSession } from './auth/session'

const router = useRouter()

async function logout() {
  clearSession()
  await router.push('/')
}
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>

  <header class="site-header">
    <RouterLink class="brand" to="/" aria-label="海洋生态数字电子书首页">
      <span class="brand-mark" aria-hidden="true"></span>
      <span>海洋生态数字电子书</span>
    </RouterLink>
    <nav class="site-nav" aria-label="主导航">
      <RouterLink to="/">首页</RouterLink>
      <RouterLink v-if="authState.user" to="/profile">个人资料</RouterLink>
      <RouterLink v-else to="/login">登录</RouterLink>
      <button v-if="authState.user" class="nav-button" type="button" @click="logout">退出登录</button>
    </nav>
  </header>

  <div id="main-content"><RouterView /></div>
</template>
