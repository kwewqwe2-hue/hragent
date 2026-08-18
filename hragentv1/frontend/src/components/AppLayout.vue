<template>
  <el-container class="shell">
    <el-aside width="232px" class="sidebar">
      <div class="brand">
        <div class="brand-mark">HR</div>
        <div>
          <strong>HRAgent</strong>
          <span>请假 SaaS Demo</span>
        </div>
      </div>
      <el-menu
        router
        :default-active="$route.path"
        :default-openeds="defaultOpeneds"
        background-color="#162033"
        text-color="#d8deea"
        active-text-color="#ffffff"
        class="menu"
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-sub-menu v-if="item.children" :index="item.path">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.label }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
              <el-icon><component :is="child.icon" /></el-icon>
              <span>{{ child.label }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <el-select
          v-if="auth.workspaces.length"
          class="workspace-select"
          :model-value="auth.user?.tenantId"
          placeholder="选择企业空间"
          @change="switchWorkspace"
        >
          <el-option
            v-for="workspace in auth.workspaces"
            :key="workspace.workspaceId"
            :label="workspace.name"
            :value="workspace.workspaceId"
          >
            <div class="workspace-option">
              <span>{{ workspace.name }}</span>
              <small>{{ workspace.status === 'ACTIVE' ? roleText(workspace.role) : statusText(workspace.status) }}</small>
            </div>
          </el-option>
        </el-select>
        <div v-else class="workspace-placeholder">尚未加入企业空间</div>

        <el-dropdown trigger="click" @command="handleAccountCommand">
          <button class="account-trigger" type="button">
            <el-avatar :size="34" :src="auth.user?.avatarUrl">{{ accountInitial }}</el-avatar>
            <span class="account-copy">
              <strong>{{ auth.user?.name }}</strong>
              <small>{{ auth.hasActiveWorkspace ? roleLabel : auth.user?.publicId }}</small>
            </span>
            <el-icon><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="account" :icon="User">个人设置</el-dropdown-item>
              <el-dropdown-item command="workspace" :icon="OfficeBuilding">企业空间</el-dropdown-item>
              <el-dropdown-item divided command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ChatDotRound,
  Check,
  Connection,
  DataLine,
  Document,
  Files,
  Grid,
  House,
  List,
  Notebook,
  OfficeBuilding,
  Postcard,
  Setting,
  SwitchButton,
  Upload,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import type { MembershipStatus, Role } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

interface MenuItem {
  path: string
  label: string
  icon: Component
  children?: MenuItem[]
}

const accountInitial = computed(() => (auth.user?.name || auth.user?.username || 'U').slice(0, 1).toUpperCase())

const roleLabel = computed(() => roleText(auth.user?.role))

function roleText(role?: Role) {
  if (role === 'NEW_HIRE') return '新入职员工'
  if (role === 'EMPLOYEE') return '员工'
  if (role === 'MANAGER') return '主管'
  if (role === 'HR') return '空间管理员'
  return '未分配角色'
}

function statusText(status: MembershipStatus) {
  if (status === 'PENDING') return '等待审核'
  if (status === 'PENDING_PROFILE') return '待建档'
  if (status === 'REJECTED') return '已拒绝'
  if (status === 'DISABLED') return '已停用'
  return status
}

const menuItems = computed<MenuItem[]>(() => {
  if (auth.user?.platformAdmin) {
    return [
      { path: '/platform-admin', label: '平台管理', icon: Grid },
      { path: '/workspace', label: '企业空间', icon: OfficeBuilding }
    ]
  }
  if (!auth.hasActiveWorkspace) {
    return [{ path: '/workspace', label: '企业空间', icon: OfficeBuilding }]
  }
  const common: MenuItem[] = [
    { path: '/dashboard', label: '工作台', icon: House },
    { path: '/assistant', label: '智能助手', icon: ChatDotRound },
    { path: '/knowledge', label: '知识库', icon: Notebook }
  ]
  if (auth.user?.role === 'NEW_HIRE') {
    return [
      { path: '/onboarding', label: '入职办理', icon: Postcard },
      common[1],
      { path: '/workspace', label: '企业空间', icon: OfficeBuilding }
    ]
  }
  if (auth.user?.role === 'EMPLOYEE') {
    return [
      common[0],
      { path: '/personal-info', label: '个人信息', icon: Postcard },
      { path: '/certificates', label: '在职证明', icon: Document },
      { path: '/my-leave', label: '我的请假', icon: Document },
      { path: '/directory', label: '组织通讯录', icon: User },
      common[1],
      common[2],
      { path: '/open-platform', label: '开放平台', icon: Connection },
      { path: '/workspace', label: '企业空间', icon: OfficeBuilding }
    ]
  }
  if (auth.user?.role === 'MANAGER') {
    return [
      common[0],
      { path: '/personal-info', label: '个人信息', icon: Postcard },
      { path: '/certificates', label: '在职证明', icon: Document },
      { path: '/my-leave', label: '我的请假', icon: Document },
      { path: '/manager-approval', label: '主管审批', icon: Check },
      { path: '/directory', label: '组织通讯录', icon: User },
      common[1],
      common[2],
      { path: '/open-platform', label: '开放平台', icon: Connection },
      { path: '/workspace', label: '企业空间', icon: OfficeBuilding }
    ]
  }
  return [
    common[0],
    { path: '/onboarding', label: '入职管理', icon: Postcard },
    { path: '/my-leave', label: '我的请假', icon: Document },
    { path: '/certificates', label: '证明管理', icon: Document },
    common[1],
    {
      path: 'leave-management',
      label: '请假管理',
      icon: Files,
      children: [
        { path: '/hr-record', label: '管理员备案', icon: Check },
        { path: '/all-records', label: '请假记录', icon: List }
      ]
    },
    {
      path: 'organization-management',
      label: '组织与成员',
      icon: UserFilled,
      children: [
        { path: '/members', label: '空间成员', icon: UserFilled },
        { path: '/directory', label: '组织通讯录', icon: User },
        { path: '/employees', label: '员工数据', icon: User },
        { path: '/personal-info', label: '个人信息库', icon: Postcard },
        { path: '/organization', label: '组织配置', icon: OfficeBuilding },
        { path: '/imports', label: '数据导入', icon: Upload }
      ]
    },
    {
      path: 'interface-debug',
      label: '接口调试',
      icon: Connection,
      children: [
        { path: '/open-platform', label: '开放平台', icon: Connection },
        { path: '/api-center', label: '企业接口', icon: Connection },
        { path: '/ai-config', label: '智能体配置', icon: Setting }
      ]
    },
    {
      path: 'content-audit',
      label: '内容与审计',
      icon: DataLine,
      children: [
        common[2],
        { path: '/logs', label: '调用与日志', icon: DataLine }
      ]
    },
    { path: '/workspace', label: '企业空间', icon: OfficeBuilding }
  ]
})

const defaultOpeneds = computed(() => menuItems.value
  .filter((item) => item.children?.some((child) => child.path === route.path))
  .map((item) => item.path))

async function switchWorkspace(workspaceId: number) {
  await auth.selectWorkspace(workspaceId)
  router.push(auth.hasActiveWorkspace ? '/dashboard' : '/workspace')
}

async function handleAccountCommand(command: string) {
  if (command === 'account') {
    router.push('/account')
    return
  }
  if (command === 'workspace') {
    router.push('/workspace')
    return
  }
  if (command === 'logout') {
    await auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
}

.sidebar {
  background: #162033;
  border-right: 1px solid #101828;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding: 0 18px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #2f80ed;
  font-weight: 800;
}

.brand strong,
.brand span {
  display: block;
}

.brand span {
  margin-top: 2px;
  color: #aeb7c7;
  font-size: 12px;
}

.menu {
  border-right: 0;
  background: #162033;
}

.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 44px;
  margin: 6px 10px;
  color: #d8deea !important;
  border-radius: 8px;
}

.menu :deep(.el-menu),
.menu :deep(.el-sub-menu .el-menu) {
  background: #101827 !important;
}

.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) {
  color: #fff !important;
  background: #24324b !important;
}

.menu :deep(.el-menu-item.is-active) {
  color: #fff !important;
  background: #2f80ed !important;
}

.topbar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid #d9e0ea;
}

.workspace-select {
  width: min(320px, 42vw);
}

.workspace-placeholder {
  color: #687386;
  font-size: 13px;
}

.workspace-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.workspace-option small {
  color: #98a2b3;
}

.account-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 4px 6px;
  border: 0;
  background: transparent;
  color: #172033;
  cursor: pointer;
}

.account-copy {
  min-width: 100px;
  text-align: left;
}

.account-copy strong,
.account-copy small {
  display: block;
}

.account-copy small {
  margin-top: 2px;
  color: #687386;
}

.main {
  padding: 20px;
  background: #eef2f7;
}

@media (max-width: 700px) {
  .account-copy {
    display: none;
  }

  .workspace-select {
    width: min(220px, 54vw);
  }
}
</style>
