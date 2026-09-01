<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { login } from '../auth/auth-api'
import { setSession } from '../auth/session'

const router = useRouter()
const route = useRoute()
const form = reactive({ account: '', password: '' })
const errorMessage = ref('')
const submitting = ref(false)

async function submit() {
  errorMessage.value = ''
  submitting.value = true
  try {
    const result = await login({ account: form.account.trim(), password: form.password })
    setSession(result.token, result.user)
    const candidate = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    const redirect = candidate.startsWith('/') && !candidate.startsWith('//') ? candidate : '/'
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
    <a-card class="auth-card" :bordered="false" aria-labelledby="login-title">
      <p class="section-kicker">WELCOME BACK</p>
      <h1 id="login-title">登录平台</h1>
      <p class="auth-intro">使用用户名或邮箱登录，继续探索海洋生态知识。</p>

      <a-form class="auth-form" :model="form" layout="vertical" @finish="submit">
        <a-form-item label="用户名或邮箱" name="account" :rules="[{ required: true, message: '请输入用户名或邮箱' }]">
          <a-input v-model:value="form.account" autocomplete="username" maxlength="255" />
        </a-form-item>
        <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="form.password" autocomplete="current-password" maxlength="64" />
        </a-form-item>
        <a-alert v-if="errorMessage" class="form-alert" type="error" :message="errorMessage" show-icon />
        <a-button class="form-button" type="primary" html-type="submit" block :loading="submitting">登录</a-button>
      </a-form>

      <p class="auth-switch">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
    </a-card>
  </main>
</template>
