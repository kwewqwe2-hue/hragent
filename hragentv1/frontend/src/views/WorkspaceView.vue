<template>
  <div>
    <div class="page-title">
      <div>
        <h1>企业空间</h1>
        <p>账号ID：{{ auth.user?.publicId }}</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Link" @click="joinVisible = true">申请加入</el-button>
        <el-button type="primary" :icon="Plus" @click="createVisible = true">创建空间</el-button>
      </div>
    </div>

    <div v-if="auth.workspaces.length" class="workspace-grid">
      <article v-for="workspace in auth.workspaces" :key="workspace.workspaceId" class="workspace-card">
        <div class="workspace-head">
          <div class="workspace-mark">{{ workspace.name.slice(0, 1).toUpperCase() }}</div>
          <div class="workspace-name">
            <strong>{{ workspace.name }}</strong>
            <span>{{ workspace.code }}</span>
          </div>
          <el-tag :type="statusType(workspace.status)">{{ statusLabel(workspace.status) }}</el-tag>
        </div>
        <div class="workspace-meta">
          <span>{{ roleLabel(workspace.role) }}</span>
          <span>{{ workspace.memberCount }} 名成员</span>
        </div>
        <el-alert
          v-if="workspace.status === 'PENDING_PROFILE'"
          type="warning"
          :closable="false"
          title="管理员已同意加入，等待员工档案导入"
        />
        <div class="workspace-actions">
          <el-button
            :type="workspace.status === 'ACTIVE' ? 'primary' : 'default'"
            :disabled="workspace.status !== 'ACTIVE'"
            @click="openWorkspace(workspace)"
          >
            进入空间
          </el-button>
          <el-button
            v-if="workspace.status === 'ACTIVE'"
            :icon="SwitchButton"
            @click="leaveWorkspace(workspace)"
          >
            退出企业
          </el-button>
        </div>
      </article>
    </div>

    <el-empty v-else description="当前账号还没有企业空间" />

    <el-dialog v-model="createVisible" title="创建企业空间" width="480px">
      <el-form label-position="top">
        <el-form-item label="空间名称">
          <el-input v-model="createName" placeholder="例如 星辰科技" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="createWorkspace">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="joinVisible" title="申请加入空间" width="480px">
      <el-form label-position="top">
        <el-form-item label="空间码">
          <el-input v-model="joinCode" placeholder="SPC-XXXXXXXX" />
        </el-form-item>
        <el-divider content-position="left">员工信息（选填）</el-divider>
        <el-alert
          type="info"
          :closable="false"
          title="知道的信息可以先填写；不确定的项目留空即可，由空间管理员审核建档。"
        />
        <el-row :gutter="12" class="draft-fields">
          <el-col :span="12">
            <el-form-item label="工号">
              <el-input v-model="joinProfile.employeeNo" placeholder="不知道可留空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="joinProfile.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门">
              <el-input v-model="joinProfile.department" placeholder="例如 研发部" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-input v-model="joinProfile.title" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="直属主管工号">
              <el-input v-model="joinProfile.managerEmployeeNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker v-model="joinProfile.entryDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="joinVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="joinWorkspace">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Link, Plus, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postData } from '../api/http'
import type { MembershipStatus, Role, WorkspaceSummary } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const createVisible = ref(false)
const joinVisible = ref(false)
const createName = ref('')
const joinCode = ref('')
const joinProfile = reactive({
  employeeNo: '',
  phone: '',
  department: '',
  title: '',
  managerEmployeeNo: '',
  entryDate: ''
})
const submitting = ref(false)

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

function statusType(status: MembershipStatus) {
  if (status === 'ACTIVE') return 'success'
  if (status === 'REJECTED' || status === 'DISABLED') return 'danger'
  return 'warning'
}

function roleLabel(role: Role) {
  if (role === 'HR') return '空间管理员'
  if (role === 'MANAGER') return '主管'
  return '员工'
}

async function openWorkspace(workspace: WorkspaceSummary) {
  await auth.selectWorkspace(workspace.workspaceId)
  router.push('/dashboard')
}

async function createWorkspace() {
  if (!createName.value.trim()) {
    ElMessage.warning('请输入空间名称')
    return
  }
  submitting.value = true
  try {
    const workspace = await postData<WorkspaceSummary>('/workspaces', { name: createName.value })
    await auth.refreshWorkspaces()
    await auth.selectWorkspace(workspace.workspaceId)
    createVisible.value = false
    createName.value = ''
    ElMessage.success('企业空间已创建')
    router.push('/dashboard')
  } finally {
    submitting.value = false
  }
}

async function joinWorkspace() {
  if (!joinCode.value.trim()) {
    ElMessage.warning('请输入空间码')
    return
  }
  submitting.value = true
  try {
    await postData<WorkspaceSummary>('/workspaces/join', {
      workspaceCode: joinCode.value,
      ...joinProfile
    })
    await auth.refreshWorkspaces()
    joinVisible.value = false
    joinCode.value = ''
    Object.assign(joinProfile, {
      employeeNo: '',
      phone: '',
      department: '',
      title: '',
      managerEmployeeNo: '',
      entryDate: ''
    })
    ElMessage.success('加入申请已提交')
  } finally {
    submitting.value = false
  }
}

async function leaveWorkspace(workspace: WorkspaceSummary) {
  await ElMessageBox.confirm(
    `退出“${workspace.name}”后将无法继续访问该企业数据，历史记录会保留。确认退出？`,
    '退出企业',
    {
      confirmButtonText: '确认退出',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  await auth.leaveWorkspace(workspace.workspaceId)
  ElMessage.success('已退出企业空间')
  router.push('/workspace')
}

onMounted(auth.refreshWorkspaces)
</script>

<style scoped>
.page-actions {
  display: flex;
  gap: 10px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.workspace-card {
  padding: 16px;
  background: #fff;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
}

.workspace-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.workspace-mark {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  display: grid;
  place-items: center;
  color: #fff;
  background: #2f80ed;
  border-radius: 8px;
  font-weight: 800;
}

.workspace-name {
  min-width: 0;
  flex: 1;
}

.workspace-name strong,
.workspace-name span {
  display: block;
}

.workspace-name strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-name span {
  margin-top: 3px;
  color: #687386;
  font-size: 12px;
}

.workspace-meta {
  display: flex;
  justify-content: space-between;
  margin: 18px 0 14px;
  color: #687386;
  font-size: 13px;
}

.workspace-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 14px;
}

.workspace-actions .el-button {
  margin: 0;
}

.draft-fields {
  margin-top: 12px;
}

@media (max-width: 560px) {
  .page-title {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
