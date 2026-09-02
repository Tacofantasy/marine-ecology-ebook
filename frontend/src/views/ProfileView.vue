<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '../api/client'
import { getCurrentUser } from '../auth/auth-api'
import { clearSession, refreshUser, type UserProfile } from '../auth/session'

const router = useRouter()
const loading = ref(true)
const errorMessage = ref('')
const user = ref<UserProfile | null>(null)

const roleLabel = computed(() => {
  const role = user.value?.role
  return role === 'SUPER_ADMIN' ? '总管理员' : role === 'ADMIN' ? '子管理员' : '注册用户'
})
const userInitial = computed(() => user.value?.displayName?.trim().charAt(0).toUpperCase() || '客')

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
  <main class="page-shell">
    <a-card class="auth-card profile-card" :bordered="false" aria-labelledby="profile-title">
      <p class="section-kicker">ACCOUNT PROFILE</p>
      <a-spin v-if="loading" tip="正在验证登录状态…" />
      <a-alert v-else-if="errorMessage" type="error" :message="errorMessage" show-icon />
      <template v-else-if="user">
        <header class="profile-header">
          <span class="profile-avatar" aria-hidden="true">{{ userInitial }}</span>
          <div>
            <h1 id="profile-title">{{ user.displayName }}</h1>
            <span class="profile-role">{{ roleLabel }}</span>
          </div>
        </header>
        <dl class="profile-list">
          <div><dt>登录名</dt><dd>{{ user.username }}</dd></div>
          <div><dt>昵称</dt><dd>{{ user.displayName }}</dd></div>
          <div><dt>邮箱</dt><dd>{{ user.email || '未设置' }}</dd></div>
          <div><dt>角色</dt><dd>{{ roleLabel }}</dd></div>
        </dl>
        <RouterLink custom to="/" v-slot="{ navigate }"><a-button class="profile-back" @click="navigate">返回首页</a-button></RouterLink>
      </template>
    </a-card>
  </main>
</template>
