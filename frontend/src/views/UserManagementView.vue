<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { authState } from '../auth/session'
import {
  createSubAdministrator,
  deactivateUser,
  getAdminUsers,
  resetUserPassword,
  updateUserStatus,
  type AdminUserItem,
} from '../auth/admin-user-api'

const users = ref<AdminUserItem[]>([])
const total = ref(0)
const loading = ref(false)
const errorText = ref('')
const actionKey = ref<string | null>(null)
const createModalOpen = ref(false)
const passwordModalOpen = ref(false)
const selectedUser = ref<AdminUserItem | null>(null)
const submitting = ref(false)
const filters = reactive({ keyword: '', role: undefined as 'ADMIN' | 'USER' | undefined, status: undefined as number | undefined, page: 1, pageSize: 10 })
const createForm = reactive({ username: '', displayName: '', email: '', password: '' })
const passwordForm = reactive({ password: '' })

const isSuperAdmin = computed(() => authState.user?.role === 'SUPER_ADMIN')
const roleOptions = computed(() => isSuperAdmin.value
  ? [{ value: 'ADMIN', label: '子管理员' }, { value: 'USER', label: '注册用户' }]
  : [{ value: 'USER', label: '注册用户' }])
const roleLabels: Record<string, string> = { ADMIN: '子管理员', USER: '注册用户' }
const roleColors: Record<string, string> = { ADMIN: 'geekblue', USER: 'cyan' }

function actionFor(record: AdminUserItem, action: string) {
  return `${record.id}:${action}`
}

function formatTime(value: string | null) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value)) : '—'
}

async function loadUsers() {
  loading.value = true
  errorText.value = ''
  try {
    const data = await getAdminUsers(filters)
    users.value = data.list
    total.value = data.total
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '用户列表加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function search() {
  filters.page = 1
  void loadUsers()
}

function resetFilters() {
  filters.keyword = ''
  filters.role = undefined
  filters.status = undefined
  filters.page = 1
  void loadUsers()
}

function changePage(page: number, pageSize: number) {
  filters.page = page
  filters.pageSize = pageSize
  void loadUsers()
}

function openCreate() {
  createForm.username = ''
  createForm.displayName = ''
  createForm.email = ''
  createForm.password = ''
  createModalOpen.value = true
}

function openPasswordReset(record: AdminUserItem) {
  selectedUser.value = record
  passwordForm.password = ''
  passwordModalOpen.value = true
}

async function submitCreate() {
  const username = createForm.username.trim()
  const displayName = createForm.displayName.trim()
  if (!/^[A-Za-z0-9_]{3,64}$/.test(username)) {
    message.warning('登录名须为 3 至 64 位字母、数字或下划线')
    return
  }
  if (!displayName) {
    message.warning('请输入显示昵称')
    return
  }
  if (createForm.password.length < 8 || createForm.password.length > 64) {
    message.warning('初始密码须为 8 至 64 位')
    return
  }
  submitting.value = true
  try {
    await createSubAdministrator({
      username,
      displayName,
      email: createForm.email.trim() || undefined,
      password: createForm.password,
    })
    message.success('子管理员已创建，请将初始密码安全地告知对方')
    createModalOpen.value = false
    filters.role = 'ADMIN'
    filters.status = 1
    filters.page = 1
    await loadUsers()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '创建子管理员失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

async function changeStatus(record: AdminUserItem, status: 0 | 1) {
  const key = actionFor(record, `status-${status}`)
  actionKey.value = key
  try {
    await updateUserStatus(record.id, status)
    message.success(status === 0 ? `已禁用账号 ${record.username}` : `已启用账号 ${record.username}`)
    await loadUsers()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '账号状态更新失败，请稍后重试。')
  } finally {
    actionKey.value = null
  }
}

async function submitPasswordReset() {
  if (!selectedUser.value) return
  if (passwordForm.password.length < 8 || passwordForm.password.length > 64) {
    message.warning('新密码须为 8 至 64 位')
    return
  }
  submitting.value = true
  try {
    await resetUserPassword(selectedUser.value.id, passwordForm.password)
    message.success(`已重置 ${selectedUser.value.username} 的密码，对方需要重新登录`)
    passwordModalOpen.value = false
  } catch (error) {
    message.error(error instanceof Error ? error.message : '密码重置失败，请稍后重试。')
  } finally {
    submitting.value = false
  }
}

async function remove(record: AdminUserItem) {
  const key = actionFor(record, 'delete')
  actionKey.value = key
  try {
    await deactivateUser(record.id)
    message.success(`已注销账号 ${record.username}`)
    if (users.value.length === 1 && filters.page > 1) filters.page -= 1
    await loadUsers()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '注销失败，请稍后重试。')
  } finally {
    actionKey.value = null
  }
}

onMounted(loadUsers)
</script>

<template>
  <main class="admin-page page-shell">
    <header class="admin-page-header">
      <div>
        <p class="section-kicker">平台运营</p>
        <h1>账号管理</h1>
        <p v-if="isSuperAdmin">管理注册用户与子管理员。禁用可恢复；注销不可恢复且会立即使账号下线。</p>
        <p v-else>管理注册用户。禁用可恢复；注销不可恢复且会立即使账号下线。</p>
      </div>
      <a-button v-if="isSuperAdmin" type="primary" @click="openCreate">新增子管理员</a-button>
    </header>

    <a-card :bordered="false" class="management-card">
      <a-form class="management-filters" layout="inline" @submit.prevent="search">
        <a-form-item label="关键词">
          <a-input v-model:value="filters.keyword" allow-clear placeholder="登录名或昵称" autocomplete="off" @press-enter="search" />
        </a-form-item>
        <a-form-item label="账号类型">
          <a-select v-model:value="filters.role" allow-clear placeholder="全部可管理账号" :options="roleOptions" @change="search" />
        </a-form-item>
        <a-form-item label="账号状态">
          <a-select v-model:value="filters.status" allow-clear placeholder="全部状态" :options="[{ value: 1, label: '正常' }, { value: 0, label: '已禁用' }]" @change="search" />
        </a-form-item>
        <a-form-item><a-button html-type="submit" type="primary">搜索</a-button></a-form-item>
        <a-form-item v-if="filters.keyword || filters.role || filters.status !== undefined"><a-button @click="resetFilters">重置</a-button></a-form-item>
      </a-form>

      <div class="management-summary"><span>共 {{ total }} 个可管理账号</span><span>{{ isSuperAdmin ? '总管理员可维护注册用户与子管理员' : '子管理员仅维护注册用户' }}</span></div>

      <a-alert v-if="errorText" class="management-alert" type="error" :message="errorText" show-icon>
        <template #action><a-button size="small" @click="loadUsers">重新加载</a-button></template>
      </a-alert>

      <a-table
        v-else
        class="desktop-management-table"
        :data-source="users"
        :loading="loading"
        :pagination="false"
        row-key="id"
        :scroll="{ x: 880 }"
      >
        <a-table-column title="登录名" data-index="username" key="username" :width="150" />
        <a-table-column title="昵称" data-index="displayName" key="displayName" :width="140" />
        <a-table-column title="邮箱" key="email" :width="210"><template #default="{ record }">{{ record.email || '—' }}</template></a-table-column>
        <a-table-column title="角色" key="role" :width="110"><template #default="{ record }"><a-tag :color="roleColors[record.role] ?? 'default'">{{ roleLabels[record.role] ?? record.role }}</a-tag></template></a-table-column>
        <a-table-column title="状态" key="status" :width="100"><template #default="{ record }"><a-tag :color="record.status === 1 ? 'green' : 'orange'">{{ record.status === 1 ? '正常' : '已禁用' }}</a-tag></template></a-table-column>
        <a-table-column title="注册时间" key="createdAt" :width="175"><template #default="{ record }">{{ formatTime(record.createdAt) }}</template></a-table-column>
        <a-table-column title="操作" key="actions" :width="280" fixed="right">
          <template #default="{ record }">
            <a-space :size="0" wrap>
              <a-popconfirm v-if="record.status === 1" :title="`确定禁用账号 ${record.username} 吗？该账号将立即下线，之后可重新启用。`" ok-text="禁用" ok-type="danger" cancel-text="取消" @confirm="changeStatus(record, 0)">
                <a-button :loading="actionKey === actionFor(record, 'status-0')" danger type="link">禁用</a-button>
              </a-popconfirm>
              <a-popconfirm v-else :title="`确定启用账号 ${record.username} 吗？`" ok-text="启用" cancel-text="取消" @confirm="changeStatus(record, 1)">
                <a-button :loading="actionKey === actionFor(record, 'status-1')" type="link">启用</a-button>
              </a-popconfirm>
              <a-button v-if="isSuperAdmin" type="link" @click="openPasswordReset(record)">重置密码</a-button>
              <a-popconfirm :title="`确定注销账号 ${record.username} 吗？此操作不可恢复，该账号将立即下线。`" ok-text="注销" ok-type="danger" cancel-text="取消" @confirm="remove(record)">
                <a-button :loading="actionKey === actionFor(record, 'delete')" danger type="link">注销</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table-column>
      </a-table>

      <div v-if="!errorText" class="mobile-management-list" :aria-busy="loading">
        <article v-for="record in users" :key="record.id" class="mobile-management-card">
          <div class="mobile-management-heading">
            <div class="mobile-user-identity"><span>{{ record.displayName.trim().charAt(0).toUpperCase() }}</span><div><strong>{{ record.displayName }}</strong><small>{{ record.username }}</small></div></div>
            <a-tag :color="record.status === 1 ? 'green' : 'orange'">{{ record.status === 1 ? '正常' : '已禁用' }}</a-tag>
          </div>
          <dl>
            <div><dt>角色</dt><dd>{{ roleLabels[record.role] ?? record.role }}</dd></div>
            <div><dt>邮箱</dt><dd>{{ record.email || '未设置' }}</dd></div>
            <div><dt>注册时间</dt><dd>{{ formatTime(record.createdAt) }}</dd></div>
          </dl>
          <div class="mobile-management-actions">
            <a-popconfirm v-if="record.status === 1" :title="`确定禁用账号 ${record.username} 吗？该账号将立即下线，之后可重新启用。`" ok-text="禁用" ok-type="danger" cancel-text="取消" @confirm="changeStatus(record, 0)"><a-button :loading="actionKey === actionFor(record, 'status-0')" danger>禁用</a-button></a-popconfirm>
            <a-popconfirm v-else :title="`确定启用账号 ${record.username} 吗？`" ok-text="启用" cancel-text="取消" @confirm="changeStatus(record, 1)"><a-button :loading="actionKey === actionFor(record, 'status-1')">启用</a-button></a-popconfirm>
            <a-button v-if="isSuperAdmin" @click="openPasswordReset(record)">重置密码</a-button>
            <a-popconfirm :title="`确定注销账号 ${record.username} 吗？此操作不可恢复，该账号将立即下线。`" ok-text="注销" ok-type="danger" cancel-text="取消" @confirm="remove(record)"><a-button :loading="actionKey === actionFor(record, 'delete')" danger>注销</a-button></a-popconfirm>
          </div>
        </article>
      </div>

      <p v-if="!loading && !errorText && users.length === 0" class="management-empty">{{ filters.keyword || filters.role || filters.status !== undefined ? '未找到符合条件的账号，请调整筛选条件。' : '暂无可管理账号。' }}</p>
      <a-pagination v-if="!errorText && total > filters.pageSize" class="management-pagination" :current="filters.page" :page-size="filters.pageSize" :total="total" :page-size-options="['10', '20']" show-size-changer @change="changePage" />
    </a-card>

    <a-modal v-model:open="createModalOpen" :confirm-loading="submitting" title="新增子管理员" @ok="submitCreate">
      <a-form layout="vertical" @submit.prevent="submitCreate">
        <a-form-item label="登录名" required><a-input v-model:value="createForm.username" :maxlength="64" autocomplete="username" placeholder="3–64 位字母、数字或下划线" /></a-form-item>
        <a-form-item label="显示昵称" required><a-input v-model:value="createForm.displayName" :maxlength="64" autocomplete="off" /></a-form-item>
        <a-form-item label="联系邮箱"><a-input v-model:value="createForm.email" type="email" :maxlength="255" autocomplete="email" /></a-form-item>
        <a-form-item label="初始密码" required><a-input-password v-model:value="createForm.password" :maxlength="64" autocomplete="new-password" placeholder="8–64 位" /></a-form-item>
        <p class="form-hint">请通过安全渠道将初始密码告知新管理员；对方可在首次登录后使用。</p>
      </a-form>
    </a-modal>

    <a-modal v-model:open="passwordModalOpen" :confirm-loading="submitting" :title="selectedUser ? `重置 ${selectedUser.username} 的密码` : '重置密码'" @ok="submitPasswordReset">
      <a-form layout="vertical" @submit.prevent="submitPasswordReset">
        <a-form-item label="新密码" required><a-input-password v-model:value="passwordForm.password" :maxlength="64" autocomplete="new-password" placeholder="8–64 位" /></a-form-item>
        <p class="form-hint">保存后会立即使该账号当前会话失效，用户需使用新密码重新登录。</p>
      </a-form>
    </a-modal>
  </main>
</template>
