<template>
  <div>
    <div class="page-title">
      <div>
        <h1>空间成员</h1>
        <p>{{ auth.user?.workspaceName }} · {{ auth.user?.workspaceCode }}</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <section class="content-panel requests-panel">
      <div class="toolbar-row">
        <strong>加入申请</strong>
        <el-badge :value="requests.length" :hidden="requests.length === 0" />
      </div>
      <el-table :data="requests" stripe empty-text="暂无待处理申请">
        <el-table-column label="账号" min-width="180">
          <template #default="{ row }">
            <div class="member-cell">
              <el-avatar :size="34" :src="row.avatarUrl">{{ row.name.slice(0, 1) }}</el-avatar>
              <div><strong>{{ row.name }}</strong><span>{{ row.publicId }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="自填资料" min-width="220">
          <template #default="{ row }">
            <span class="draft-summary">{{ draftSummary(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="180" />
        <el-table-column label="操作" width="170">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="review(row, true)">同意</el-button>
            <el-button size="small" @click="review(row, false)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="content-panel members-panel">
      <div class="toolbar-row">
        <strong>全部成员</strong>
        <span class="muted">{{ members.length }} 人</span>
      </div>
      <el-table :data="members" stripe>
        <el-table-column label="成员" min-width="190">
          <template #default="{ row }">
            <div class="member-cell">
              <el-avatar :size="34" :src="row.avatarUrl">{{ row.name.slice(0, 1) }}</el-avatar>
              <div><strong>{{ row.name }}</strong><span>{{ row.publicId }}</span></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="employeeNo" label="工号" width="120">
          <template #default="{ row }">{{ row.employeeNo || '待建档' }}</template>
        </el-table-column>
        <el-table-column prop="department" label="部门" min-width="140" />
        <el-table-column prop="title" label="岗位" min-width="150" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="空间角色" width="160">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              :disabled="row.accountId === auth.user?.id"
              @change="(role: Role) => updateRole(row, role)"
            >
              <el-option label="员工" value="EMPLOYEE" />
              <el-option label="主管" value="MANAGER" />
              <el-option label="空间管理员" value="HR" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="建档" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING_PROFILE'"
              size="small"
              type="primary"
              :icon="Edit"
              @click="goProfile(row)"
            >
              建档
            </el-button>
            <span v-else class="muted">{{ row.employeeProfileId ? '已绑定' : '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Edit, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getData, postData, putData } from '../api/http'
import type { MembershipStatus, Role, WorkspaceMember } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const members = ref<WorkspaceMember[]>([])
const requests = ref<WorkspaceMember[]>([])

function statusLabel(status: MembershipStatus) {
  return {
    PENDING: '等待审核',
    PENDING_PROFILE: '待建档',
    ACTIVE: '正常',
    REJECTED: '已拒绝',
    LEFT: '已离开',
    DISABLED: '已停用'
  }[status]
}

function draftSummary(member: WorkspaceMember) {
  const values = [member.draftEmployeeNo, member.draftDepartment, member.draftTitle].filter(Boolean)
  return values.length ? values.join(' / ') : '未填写，待管理员补充'
}

function goProfile(member: WorkspaceMember) {
  router.push({ path: '/employees', query: { bind: member.publicId } })
}

async function load() {
  const workspaceId = auth.user?.tenantId
  if (!workspaceId) return
  ;[members.value, requests.value] = await Promise.all([
    getData<WorkspaceMember[]>(`/workspaces/${workspaceId}/members`),
    getData<WorkspaceMember[]>(`/workspaces/${workspaceId}/join-requests`)
  ])
}

async function review(member: WorkspaceMember, approved: boolean) {
  const workspaceId = auth.user?.tenantId
  if (!workspaceId) return
  await postData(`/workspaces/${workspaceId}/join-requests/${member.membershipId}/review`, { approved })
  ElMessage.success(approved ? '已同意加入申请' : '已拒绝加入申请')
  await load()
}

async function updateRole(member: WorkspaceMember, role: Role) {
  const workspaceId = auth.user?.tenantId
  if (!workspaceId || member.role === role) return
  await putData(`/workspaces/${workspaceId}/members/${member.membershipId}/role`, { role })
  ElMessage.success('成员角色已更新')
  await load()
}

onMounted(load)
</script>

<style scoped>
.members-panel {
  margin-top: 16px;
}

.member-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-cell strong,
.member-cell span {
  display: block;
}

.member-cell span {
  margin-top: 2px;
  color: #687386;
  font-size: 12px;
}

.draft-summary {
  color: #475467;
  font-size: 13px;
}
</style>
