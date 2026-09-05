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
  <main class="page-shell personal-page profile-page-v2">
    <nav v-if="user?.role === 'USER'" class="personal-tabs" aria-label="个人空间">
      <RouterLink to="/favorites">我的收藏</RouterLink>
      <RouterLink to="/profile">账号资料</RouterLink>
    </nav>
    <a-spin :spinning="loading" tip="正在验证登录状态…">
      <div v-if="errorMessage" class="state-panel state-panel-error" role="alert"><strong>账号资料暂时无法加载</strong><p>{{ errorMessage }}</p></div>
      <template v-else-if="user">
        <header class="personal-header profile-hero-v2">
          <span class="profile-avatar" aria-hidden="true">{{ userInitial }}</span>
          <div>
            <p class="section-kicker">ACCOUNT PROFILE</p>
            <h1 id="profile-title">{{ user.displayName }}</h1>
            <p>{{ roleLabel }} · 这是你的平台身份与联系信息。</p>
          </div>
        </header>
        <section class="profile-panel-v2" aria-labelledby="account-details-title">
          <div class="section-heading compact-heading"><div><p class="section-kicker">基本信息</p><h2 id="account-details-title">账号资料</h2></div></div>
          <dl class="profile-list">
            <div><dt>登录名</dt><dd>{{ user.username }}</dd><small>用于登录，注册后不可修改</small></div>
            <div><dt>昵称</dt><dd>{{ user.displayName }}</dd><small>用于页面欢迎语与展示</small></div>
            <div><dt>邮箱</dt><dd>{{ user.email || '未设置' }}</dd><small>当前联系邮箱</small></div>
            <div><dt>角色</dt><dd><span class="profile-role">{{ roleLabel }}</span></dd><small>决定可访问的功能范围</small></div>
          </dl>
          <div class="profile-actions-v2">
            <RouterLink class="primary-button" :to="['ADMIN', 'SUPER_ADMIN'].includes(user.role) ? '/admin' : '/favorites'">{{ ['ADMIN', 'SUPER_ADMIN'].includes(user.role) ? '进入管理台' : '查看我的收藏' }}</RouterLink>
            <RouterLink class="secondary-button" to="/">返回公共书库</RouterLink>
          </div>
        </section>
      </template>
    </a-spin>
  </main>
</template>
