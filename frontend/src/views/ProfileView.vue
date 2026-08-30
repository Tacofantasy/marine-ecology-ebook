<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { getCurrentUser } from '../auth/auth-api'
import { clearSession, refreshUser, type UserProfile } from '../auth/session'

const router = useRouter()
const loading = ref(true)
const errorMessage = ref('')
const user = ref<UserProfile | null>(null)

onMounted(async () => {
  try {
    user.value = await getCurrentUser()
    refreshUser(user.value)
  } catch (error) {
    if (error instanceof ApiError && error.code === 40101) {
      clearSession()
      await router.replace({ name: 'login', query: { redirect: '/profile' } })
      return
    }
    errorMessage.value = error instanceof ApiError ? error.message : '网络连接失败，请稍后重试。'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <main class="auth-shell">
    <section class="auth-card profile-card" aria-labelledby="profile-title">
      <p class="section-kicker">ACCOUNT PROFILE</p>
      <h1 id="profile-title">个人资料</h1>
      <p v-if="loading" role="status">正在验证登录状态…</p>
      <p v-else-if="errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
      <dl v-else-if="user" class="profile-list">
        <div><dt>用户名</dt><dd>{{ user.username }}</dd></div>
        <div><dt>邮箱</dt><dd>{{ user.email || '未设置' }}</dd></div>
        <div><dt>角色</dt><dd>{{ user.role === 'ADMIN' ? '管理员' : '注册用户' }}</dd></div>
      </dl>
      <RouterLink class="secondary-button profile-back" to="/">返回首页</RouterLink>
    </section>
  </main>
</template>
