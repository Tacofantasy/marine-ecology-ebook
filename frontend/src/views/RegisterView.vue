<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { register } from '../auth/auth-api'

const router = useRouter()
const username = ref('')
const email = ref('')
const password = ref('')
const errorMessage = ref('')
const successMessage = ref('')
const submitting = ref(false)

async function submit() {
  errorMessage.value = ''
  successMessage.value = ''
  submitting.value = true
  try {
    await register({ username: username.value.trim(), email: email.value.trim(), password: password.value })
    successMessage.value = '注册成功，正在跳转登录页面…'
    window.setTimeout(() => router.replace('/login'), 700)
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '网络连接失败，请稍后重试。'
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-shell">
    <section class="auth-card" aria-labelledby="register-title">
      <p class="section-kicker">JOIN THE PLATFORM</p>
      <h1 id="register-title">创建账号</h1>
      <p class="auth-intro">注册后可参与后续的点赞与收藏功能。</p>

      <form class="auth-form" @submit.prevent="submit">
        <div class="form-field">
          <label for="username">用户名</label>
          <input id="username" v-model="username" name="username" autocomplete="username" required pattern="[A-Za-z0-9_]{3,64}" minlength="3" maxlength="64" aria-describedby="username-hint" />
          <small id="username-hint">3 至 64 位字母、数字或下划线；`admin` 为保留名称。</small>
        </div>
        <div class="form-field">
          <label for="email">邮箱</label>
          <input id="email" v-model="email" name="email" type="email" autocomplete="email" required maxlength="255" />
        </div>
        <div class="form-field">
          <label for="register-password">密码</label>
          <input id="register-password" v-model="password" name="password" type="password" autocomplete="new-password" required minlength="8" maxlength="64" />
          <small>密码长度为 8 至 64 位。</small>
        </div>
        <p v-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
        <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>
        <button class="primary-button form-button" type="submit" :disabled="submitting">
          {{ submitting ? '注册中…' : '创建账号' }}
        </button>
      </form>

      <p class="auth-switch">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
    </section>
  </main>
</template>
