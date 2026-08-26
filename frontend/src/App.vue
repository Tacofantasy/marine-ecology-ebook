<script setup lang="ts">
import { onMounted, ref } from 'vue'

interface HealthPayload {
  service: string
  status: string
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const loading = ref(true)
const healthy = ref(false)
const statusText = ref('正在检查后端服务…')

async function checkHealth() {
  loading.value = true
  try {
    const response = await fetch('/api/health')
    const body = (await response.json()) as ApiResponse<HealthPayload>
    healthy.value = response.ok && body.code === 0 && body.data.status === 'UP'
    statusText.value = healthy.value
      ? `已连接 ${body.data.service}`
      : '后端服务暂不可用，请确认后端已启动。'
  } catch {
    healthy.value = false
    statusText.value = '无法连接后端，请确认后端已启动。'
  } finally {
    loading.value = false
  }
}

onMounted(checkHealth)
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>

  <header class="site-header">
    <a class="brand" href="/" aria-label="海洋生态数字电子书首页">
      <span class="brand-mark" aria-hidden="true"></span>
      <span>海洋生态数字电子书</span>
    </a>
    <span class="phase-label">Web 端 · 第一迭代</span>
  </header>

  <main id="main-content" class="page-shell">
    <section class="hero" aria-labelledby="hero-title">
      <p class="eyebrow">MARINE ECOLOGY · DIGITAL READING</p>
      <h1 id="hero-title">从一页知识，走进蔚蓝生态</h1>
      <p class="hero-copy">
        面向海洋科普内容的阅读与管理平台。首期将提供分类浏览、电子书阅读、互动收藏与内容管理。
      </p>

      <div class="service-card" aria-labelledby="service-title">
        <div>
          <p id="service-title" class="service-label">系统连接状态</p>
          <p
            class="service-status"
            :class="{ 'is-ready': healthy, 'is-error': !healthy && !loading }"
            role="status"
            aria-live="polite"
          >
            <span class="status-dot" aria-hidden="true"></span>
            {{ statusText }}
          </p>
        </div>
        <button class="secondary-button" type="button" :disabled="loading" @click="checkHealth">
          {{ loading ? '检查中…' : '重新检查' }}
        </button>
      </div>
    </section>

    <section class="next-steps" aria-labelledby="next-steps-title">
      <div>
        <p class="section-kicker">当前进度</p>
        <h2 id="next-steps-title">最小可运行骨架</h2>
      </div>
      <ol>
        <li>Vue 3 与 Vite 前端工程已初始化</li>
        <li>Spring Boot 健康检查接口已就绪</li>
        <li>下一步接入 MySQL 与内容管理模块</li>
      </ol>
    </section>
  </main>
</template>
