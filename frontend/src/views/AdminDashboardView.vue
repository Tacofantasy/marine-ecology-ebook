<script setup lang="ts">
import { AppstoreOutlined, ArrowRightOutlined, BookOutlined, ReadOutlined, TeamOutlined } from '@ant-design/icons-vue'
import StatsPanel from '../stats/StatsPanel.vue'
import { authState } from '../auth/session'

const shortcuts = [
  { to: '/admin/ebooks', title: '维护电子书', description: '创建草稿、完善封面与来源，管理发布状态。', icon: BookOutlined },
  { to: '/admin/categories', title: '整理内容分类', description: '维护两级主题目录，为电子书建立清晰归属。', icon: AppstoreOutlined },
  { to: '/admin/users', title: '管理平台账号', description: '查看注册用户，并按权限维护账号状态。', icon: TeamOutlined },
]
</script>

<template>
  <main class="admin-page page-shell admin-dashboard">
    <header class="admin-page-header dashboard-welcome">
      <div>
        <p class="section-kicker">内容概览</p>
        <h1>{{ authState.user?.displayName }}，欢迎回来</h1>
        <p>从这里掌握平台内容动态，或直接进入今天要处理的工作。</p>
      </div>
      <RouterLink class="secondary-button" to="/"><ReadOutlined aria-hidden="true" />查看公共书库</RouterLink>
    </header>

    <section aria-labelledby="dashboard-shortcuts-title">
      <div class="section-heading compact-heading">
        <div>
          <p class="section-kicker">快捷工作</p>
          <h2 id="dashboard-shortcuts-title">今天要做什么？</h2>
        </div>
      </div>
      <div class="admin-shortcut-grid">
        <RouterLink v-for="item in shortcuts" :key="item.to" :to="item.to" class="admin-shortcut-card">
          <span class="admin-shortcut-icon" aria-hidden="true"><component :is="item.icon" /></span>
          <span class="admin-shortcut-copy"><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span>
          <ArrowRightOutlined class="admin-shortcut-arrow" aria-hidden="true" />
        </RouterLink>
      </div>
    </section>

    <section class="dashboard-stats" aria-labelledby="dashboard-stats-title">
      <div class="section-heading compact-heading">
        <div>
          <p class="section-kicker">平台动态</p>
          <h2 id="dashboard-stats-title">阅读与互动趋势</h2>
        </div>
      </div>
      <StatsPanel />
    </section>
  </main>
</template>
