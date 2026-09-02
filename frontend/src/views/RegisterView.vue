<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CheckCircleOutlined } from '@ant-design/icons-vue'
import { ApiError } from '../api/client'
import { register } from '../auth/auth-api'

const router = useRouter()
const form = reactive({ username: '', email: '', password: '' })
const errorMessage = ref('')
const successMessage = ref('')
const submitting = ref(false)

async function submit() {
  errorMessage.value = ''
  successMessage.value = ''
  submitting.value = true
  try {
    await register({ username: form.username.trim(), email: form.email.trim(), password: form.password })
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
    <aside class="auth-brand" aria-hidden="true">
      <p class="eyebrow">JOIN THE PLATFORM</p>
      <h2>创建一个账号，收藏属于你的海洋知识</h2>
      <p class="auth-brand-copy">注册成为平台用户，即可阅读已发布电子书，并参与后续的点赞与收藏功能。</p>
      <ul class="auth-brand-points">
        <li><CheckCircleOutlined />登录名注册后不可修改，请谨慎选择</li>
        <li><CheckCircleOutlined />昵称用于页面欢迎语与公共展示</li>
        <li><CheckCircleOutlined />注册成功后将引导至登录页面</li>
      </ul>
    </aside>

    <section class="auth-panel">
      <a-card class="auth-card" :bordered="false" aria-labelledby="register-title">
        <p class="section-kicker">JOIN THE PLATFORM</p>
        <h1 id="register-title">创建账号</h1>
        <p class="auth-intro">注册后可参与后续的点赞与收藏功能。</p>

        <a-form class="auth-form" :model="form" layout="vertical" @finish="submit">
          <a-form-item label="登录名" name="username" :rules="[{ required: true, pattern: /^[A-Za-z0-9_]{3,64}$/, message: '请输入 3 至 64 位字母、数字或下划线' }]">
            <a-input v-model:value="form.username" autocomplete="username" maxlength="64" />
            <small id="username-hint">3 至 64 位字母、数字或下划线；`admin` 为保留名称。</small>
          </a-form-item>
          <a-form-item label="邮箱（选填）" name="email" :rules="[{ type: 'email', message: '邮箱格式不正确' }]">
            <a-input v-model:value="form.email" autocomplete="email" maxlength="255" />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, min: 8, message: '密码至少 8 位' }]">
            <a-input-password v-model:value="form.password" autocomplete="new-password" maxlength="64" />
            <small>密码长度为 8 至 64 位。</small>
          </a-form-item>
          <a-alert v-if="errorMessage" class="form-alert" type="error" :message="errorMessage" show-icon />
          <a-alert v-if="successMessage" class="form-alert" type="success" :message="successMessage" show-icon />
          <a-button class="form-button" type="primary" html-type="submit" block :loading="submitting">创建账号</a-button>
        </a-form>

        <p class="auth-switch">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
      </a-card>
    </section>
  </main>
</template>
