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
    <a-card class="auth-card profile-card" :bordered="false" aria-labelledby="profile-title">
      <p class="section-kicker">ACCOUNT PROFILE</p>
      <h1 id="profile-title">个人资料</h1>
      <a-spin v-if="loading" tip="正在验证登录状态…" />
      <a-alert v-else-if="errorMessage" type="error" :message="errorMessage" show-icon />
      <dl v-else-if="user" class="profile-list">
        <div><dt>用户名</dt><dd>{{ user.username }}</dd></div>
        <div><dt>昵称</dt><dd>{{ user.displayName }}</dd></div>
        <div><dt>邮箱</dt><dd>{{ user.email || '未设置' }}</dd></div>
        <div><dt>角色</dt><dd>{{ user.role === 'SUPER_ADMIN' ? '总管理员' : user.role === 'ADMIN' ? '子管理员' : '注册用户' }}</dd></div>
      </dl>
      <RouterLink custom to="/" v-slot="{ navigate }"><a-button class="profile-back" @click="navigate">返回首页</a-button></RouterLink>
    </a-card>
  </main>
</template>
