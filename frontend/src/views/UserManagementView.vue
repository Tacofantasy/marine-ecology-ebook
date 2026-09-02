<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { authState } from '../auth/session'
import { getAdminUsers, deactivateUser, type AdminUserItem } from '../auth/admin-user-api'

const users = ref<AdminUserItem[]>([])
const total = ref(0)
const loading = ref(false)
const removingUserId = ref<string | null>(null)
const filters = reactive({ keyword: '', page: 1, pageSize: 10 })

const roleLabels: Record<string, string> = {
  SUPER_ADMIN: '总管理员',
  ADMIN: '子管理员',
  USER: '注册用户',
}

const roleColors: Record<string, string> = {
  SUPER_ADMIN: 'purple',
  ADMIN: 'geekblue',
  USER: 'cyan',
}

const isSuperAdmin = () => authState.user?.role === 'SUPER_ADMIN'

async function loadUsers() {
  loading.value = true
  try {
    const data = await getAdminUsers(filters)
    users.value = data.list
    total.value = data.total
  } catch (error) {
    message.error(error instanceof Error ? error.message : '用户列表加载失败，请稍后重试。')
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  void loadUsers()
}

function changePage(page: number, pageSize: number) {
  filters.page = page
  filters.pageSize = pageSize
  void loadUsers()
}

function canDeactivate(record: AdminUserItem): boolean {
  // 不能注销自己；子管理员只能注销注册用户；总管理员不能注销总管理员
  if (record.id === authState.user?.id) return false
  if (record.role === 'SUPER_ADMIN') return false
  if (!isSuperAdmin() && record.role !== 'USER') return false
  return record.status === 1
}

async function deactivate(record: AdminUserItem) {
  if (removingUserId.value !== null) return
  removingUserId.value = record.id
  try {
    await deactivateUser(record.id)
    message.success(`已注销账号 ${record.username}`)
    await loadUsers()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '注销失败，请稍后重试。')
  } finally {
    removingUserId.value = null
  }
}

onMounted(loadUsers)
</script>

<template>
  <main class="admin-page page-shell">
    <header class="admin-page-header">
      <div>
        <p class="section-kicker">平台运营</p>
        <h1>用户管理</h1>
        <p>查看与搜索平台账号，注销后账号立即下线且无法再登录。</p>
      </div>
    </header>

    <a-card :bordered="false" class="management-card">
      <div class="management-toolbar">
        <a-input-search
          v-model:value="filters.keyword"
          allow-clear
          class="management-search"
          placeholder="搜索用户名或昵称"
          enter-button="搜索"
          @search="search"
        />
      </div>

      <a-table
        :data-source="users"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 760 }"
      >
        <a-table-column title="用户名" data-index="username" key="username" />
        <a-table-column title="昵称" data-index="displayName" key="displayName" />
        <a-table-column title="邮箱" data-index="email" key="email" :width="200">
          <template #default="{ record }">
            {{ record.email || '—' }}
          </template>
        </a-table-column>
        <a-table-column title="角色" key="role" :width="110">
          <template #default="{ record }">
            <a-tag :color="roleColors[record.role] ?? 'default'">{{ roleLabels[record.role] ?? record.role }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="状态" key="status" :width="90">
          <template #default="{ record }">
            <a-tag :color="record.status === 1 ? 'green' : 'red'">{{ record.status === 1 ? '正常' : '已注销' }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="注册时间" data-index="createdAt" key="createdAt" :width="170" />
        <a-table-column title="操作" key="actions" :width="110">
          <template #default="{ record }">
            <a-popconfirm
              :title="`确定注销账号 ${record.username} 吗？注销后该账号将立即下线且无法登录。`"
              ok-text="注销"
              ok-type="danger"
              cancel-text="取消"
              :disabled="!canDeactivate(record)"
              @confirm="deactivate(record)"
            >
              <a-button
                :loading="removingUserId === record.id"
                :disabled="!canDeactivate(record)"
                danger
                type="link"
              >注销</a-button>
            </a-popconfirm>
          </template>
        </a-table-column>
      </a-table>

      <p v-if="!loading && users.length === 0" class="management-empty">
        {{ filters.keyword ? '未找到匹配的用户，请更换关键词。' : '暂无注册用户。' }}
      </p>

      <a-pagination
        v-if="total > filters.pageSize"
        class="management-pagination"
        :current="filters.page"
        :page-size="filters.pageSize"
        :total="total"
        :page-size-options="['10', '20']"
        show-size-changer
        @change="changePage"
      />
    </a-card>
  </main>
</template>
