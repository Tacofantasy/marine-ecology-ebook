<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { login } from '../auth/auth-api'
import { setSession } from '../auth/session'

const router = useRouter()
const route = useRoute()
const account = ref('')
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)

async function submit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    const result = await login({ account: account.value.trim(), password: password.value })
    setSession(result.token, result.user)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '网络连接失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-shell">
    <section class="auth-card" aria-labelledby="login-title">
      <p class="section-kicker">WELCOME BACK</p>
      <h1 id="login-title">登录平台</h1>
      <p class="auth-intro">使用用户名或邮箱登录，继续探索海洋生态知识。</p>

      <form class="auth-form" @submit.prevent="submit">
        <div class="form-field">
          <label for="account">用户名或邮箱</label>
          <input id="account" v-model="account" name="account" autocomplete="username" required maxlength="255" />
        </div>
        <div class="form-field">
          <label for="password">密码</label>
          <input id="password" v-model="password" name="password" type="password" autocomplete="current-password" required maxlength="64" />
        </div>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <button class="primary-button form-button" type="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="auth-switch">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
    </section>
  </main>
</template>
