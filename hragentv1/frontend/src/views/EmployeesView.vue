<template>
  <div>
    <div class="page-title">
      <div>
        <h1>员工数据</h1>
        <p>维护企业员工档案、账号绑定、组织关系和假期额度。</p>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate()">新增员工</el-button>
      </div>
    </div>

    <section class="content-panel">
      <div class="filter-row">
        <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索姓名、工号、岗位" />
        <el-select v-model="departmentFilter" clearable placeholder="全部部门">
          <el-option v-for="item in activeDepartments" :key="item.id" :label="item.name" :value="item.name" />
        </el-select>
        <span class="muted">{{ filteredEmployees.length }} 名员工</span>
      </div>
      <el-table :data="filteredEmployees" stripe highlight-current-row @row-click="openDetail">
        <el-table-column prop="employeeNo" label="工号" width="100" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column label="账号绑定" min-width="150">
          <template #default="{ row }">
            <el-tag :type="row.accountPublicId ? 'success' : 'warning'">
              {{ row.accountPublicId || '未绑定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="130" />
        <el-table-column prop="title" label="岗位" width="140" />
        <el-table-column prop="managerName" label="直属主管" width="110" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">{{ roleLabel(row.role) }}</template>
        </el-table-column>
        <el-table-column prop="entryDate" label="入职日期" width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'">{{ statusLabel(row.employeeStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="View" @click.stop="openDetail(row)">详情</el-button>
            <el-button size="small" :icon="Edit" @click.stop="openEdit(row)">编辑</el-button>
            <el-tooltip v-if="!row.accountPublicId" content="将未绑定档案密码重置为 123456" placement="top">
              <el-button size="small" :icon="Key" aria-label="重置密码" @click.stop="resetPassword(row)" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingId ? '编辑员工档案' : '单人建档'" width="820px" class="employee-dialog">
      <el-alert
        v-if="selectedMember"
        type="info"
        :closable="false"
        :title="`正在为 ${selectedMember.name}（${selectedMember.publicId}）建立员工档案`"
      />
      <el-form :model="form" label-position="top" class="employee-form">
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="绑定注册账号">
              <el-select
                v-model="form.accountPublicId"
                clearable
                filterable
                :disabled="Boolean(editingEmployee?.accountPublicId)"
                placeholder="选择已审核成员，也可暂不绑定"
                style="width: 100%"
                @change="applySelectedMember"
              >
                <el-option
                  v-for="member in bindableMembers"
                  :key="member.publicId"
                  :label="`${member.name} / ${member.publicId}`"
                  :value="member.publicId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="空间角色">
              <el-select v-model="form.role" style="width: 100%">
                <el-option label="新入职员工" value="NEW_HIRE" />
                <el-option label="员工" value="EMPLOYEE" />
                <el-option label="主管" value="MANAGER" />
                <el-option label="空间管理员" value="HR" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="工号">
              <el-input v-model="form.employeeNo" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="姓名">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="部门">
              <el-select v-model="form.department" filterable style="width: 100%">
                <el-option v-for="item in activeDepartments" :key="item.id" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="岗位">
              <el-select v-model="form.title" filterable style="width: 100%">
                <el-option v-for="item in activeJobTitles" :key="item.id" :label="item.name" :value="item.name" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="直属主管">
              <el-select v-model="form.managerEmployeeNo" clearable filterable style="width: 100%">
                <el-option
                  v-for="item in managerOptions"
                  :key="item.id"
                  :label="`${item.name} / ${item.employeeNo}`"
                  :value="item.employeeNo"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="入职日期">
              <el-date-picker v-model="form.entryDate" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :xs="24" :sm="12">
            <el-form-item label="员工状态">
              <el-select v-model="form.employeeStatus" style="width: 100%">
                <el-option label="入职办理中" value="ONBOARDING" />
                <el-option label="在职" value="ACTIVE" />
                <el-option label="停用" value="INACTIVE" />
                <el-option label="离职" value="LEFT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="档案启用"><el-switch v-model="form.active" /></el-form-item>
          </el-col>
        </el-row>

        <div class="form-section-title">假期额度</div>
        <el-table :data="balanceRows" border size="small">
          <el-table-column prop="label" label="假别" min-width="100" />
          <el-table-column label="总额度（天）" min-width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.totalDays" :min="0" :step="0.5" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="已使用（天）" min-width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.usedDays" :min="0" :max="row.totalDays" :step="0.5" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="剩余" width="90">
            <template #default="{ row }">{{ Math.max(0, row.totalDays - row.usedDays) }}</template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存档案</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :size="drawerSize" title="员工详情">
      <template v-if="detail">
        <div class="detail-head">
          <el-avatar :size="52">{{ detail.employee.name.slice(0, 1) }}</el-avatar>
          <div><h2>{{ detail.employee.name }}</h2><p>{{ detail.employee.department }} · {{ detail.employee.title }}</p></div>
        </div>
        <el-descriptions :column="2" border class="detail-block">
          <el-descriptions-item label="工号">{{ detail.employee.employeeNo }}</el-descriptions-item>
          <el-descriptions-item label="账号">{{ detail.employee.accountPublicId || '未绑定' }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleLabel(detail.employee.role) }}</el-descriptions-item>
          <el-descriptions-item label="直属主管">{{ detail.employee.managerName || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.employee.phone || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.employee.email || '未填写' }}</el-descriptions-item>
        </el-descriptions>
        <div class="form-section-title">假期余额</div>
        <el-table :data="detail.balances" border size="small">
          <el-table-column prop="leaveTypeLabel" label="假别" />
          <el-table-column prop="totalDays" label="总额度" width="90" />
          <el-table-column prop="usedDays" label="已使用" width="90" />
          <el-table-column prop="remainingDays" label="剩余" width="90" />
        </el-table>
        <div class="form-section-title">请假记录</div>
        <el-table :data="detail.requests" border size="small" empty-text="暂无请假记录">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="request-detail">
                <p><strong>请假原因：</strong>{{ row.reason }}</p>
                <p v-if="row.managerOpinion"><strong>主管意见：</strong>{{ row.managerOpinion }}</p>
                <p v-if="row.hrOpinion"><strong>管理员意见：</strong>{{ row.hrOpinion }}</p>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="leaveTypeLabel" label="假别" width="80" />
          <el-table-column label="日期" min-width="190">
            <template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template>
          </el-table-column>
          <el-table-column prop="days" label="天数" width="70" />
          <el-table-column prop="statusLabel" label="状态" width="110" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit, Key, Plus, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getData, postData, putData } from '../api/http'
import type {
  Department,
  Employee,
  EmployeeDetail,
  JobTitle,
  LeaveBalance,
  LeaveType,
  Role,
  WorkspaceMember
} from '../api/types'
import { useAuthStore } from '../stores/auth'

interface BalanceRow {
  leaveType: LeaveType
  label: string
  totalDays: number
  usedDays: number
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const employees = ref<Employee[]>([])
const departments = ref<Department[]>([])
const jobTitles = ref<JobTitle[]>([])
const members = ref<WorkspaceMember[]>([])
const keyword = ref('')
const departmentFilter = ref('')
const visible = ref(false)
const editingId = ref<number | null>(null)
const editingEmployee = ref<Employee | null>(null)
const saving = ref(false)
const detail = ref<EmployeeDetail | null>(null)
const detailVisible = ref(false)
const drawerSize = window.innerWidth <= 760 ? '100%' : '720px'

const form = reactive({
  employeeNo: '',
  accountPublicId: '',
  name: '',
  role: 'EMPLOYEE' as Role,
  phone: '',
  email: '',
  department: '',
  title: '',
  managerEmployeeNo: '',
  entryDate: '',
  employeeStatus: 'ACTIVE',
  active: true
})

const balanceRows = reactive<BalanceRow[]>([
  { leaveType: 'ANNUAL', label: '年假', totalDays: 0, usedDays: 0 },
  { leaveType: 'SICK', label: '病假', totalDays: 0, usedDays: 0 },
  { leaveType: 'PERSONAL', label: '事假', totalDays: 0, usedDays: 0 },
  { leaveType: 'MARRIAGE', label: '婚假', totalDays: 0, usedDays: 0 }
])

const activeDepartments = computed(() => departments.value.filter((item) => item.active))
const activeJobTitles = computed(() => jobTitles.value.filter((item) => item.active))
const managerOptions = computed(() => employees.value.filter((item) =>
  (item.role === 'MANAGER' || item.role === 'HR') && item.id !== editingId.value && item.active
))
const bindableMembers = computed(() => members.value.filter((member) =>
  member.status === 'PENDING_PROFILE' || member.publicId === editingEmployee.value?.accountPublicId
))
const selectedMember = computed(() => members.value.find((member) => member.publicId === form.accountPublicId))
const filteredEmployees = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return employees.value.filter((employee) => {
    const inDepartment = !departmentFilter.value || employee.department === departmentFilter.value
    const searchable = `${employee.name} ${employee.employeeNo} ${employee.title}`.toLowerCase()
    return inDepartment && (!query || searchable.includes(query))
  })
})

function roleLabel(role: Role) {
  if (role === 'NEW_HIRE') return '新入职员工'
  if (role === 'HR') return '空间管理员'
  if (role === 'MANAGER') return '主管'
  return '员工'
}

function statusLabel(status: string) {
  if (status === 'ONBOARDING') return '入职办理中'
  if (status === 'LEFT') return '离职'
  if (status === 'INACTIVE') return '停用'
  return '在职'
}

function resetBalances() {
  balanceRows.forEach((row) => {
    row.totalDays = 0
    row.usedDays = 0
  })
}

function applyBalances(balances: LeaveBalance[]) {
  resetBalances()
  balances.forEach((balance) => {
    const row = balanceRows.find((item) => item.leaveType === balance.leaveType)
    if (row) {
      row.totalDays = Number(balance.totalDays)
      row.usedDays = Number(balance.usedDays)
    }
  })
}

async function load() {
  const workspaceId = auth.user?.tenantId
  const requests: Promise<unknown>[] = [
    getData<Employee[]>('/admin/employees').then((data) => { employees.value = data }),
    getData<Department[]>('/admin/departments').then((data) => { departments.value = data }),
    getData<JobTitle[]>('/admin/job-titles').then((data) => { jobTitles.value = data })
  ]
  if (workspaceId) {
    requests.push(getData<WorkspaceMember[]>(`/workspaces/${workspaceId}/members`).then((data) => { members.value = data }))
  }
  await Promise.all(requests)
}

function baseForm() {
  return {
    employeeNo: '',
    accountPublicId: '',
    name: '',
    role: 'EMPLOYEE' as Role,
    phone: '',
    email: '',
    department: activeDepartments.value[0]?.name || '',
    title: activeJobTitles.value[0]?.name || '',
    managerEmployeeNo: '',
    entryDate: '',
    employeeStatus: 'ACTIVE',
    active: true
  }
}

function openCreate(member?: WorkspaceMember) {
  editingId.value = null
  editingEmployee.value = null
  Object.assign(form, baseForm())
  resetBalances()
  if (member) {
    form.accountPublicId = member.publicId
    applyMemberDraft(member)
  }
  visible.value = true
}

function applySelectedMember(publicId?: string) {
  const member = members.value.find((item) => item.publicId === publicId)
  if (member && !editingId.value) applyMemberDraft(member)
}

function applyMemberDraft(member: WorkspaceMember) {
  form.name = member.name
  form.email = member.email
  form.employeeNo = member.draftEmployeeNo || form.employeeNo
  form.phone = member.draftPhone || ''
  form.department = activeDepartments.value.some((item) => item.name === member.draftDepartment)
    ? member.draftDepartment || form.department
    : form.department
  form.title = activeJobTitles.value.some((item) => item.name === member.draftTitle)
    ? member.draftTitle || form.title
    : form.title
  form.managerEmployeeNo = member.draftManagerEmployeeNo || ''
  form.entryDate = member.draftEntryDate || ''
}

async function openEdit(row: Employee) {
  editingId.value = row.id
  editingEmployee.value = row
  const manager = employees.value.find((item) => item.id === row.managerId)
  Object.assign(form, {
    employeeNo: row.employeeNo,
    accountPublicId: row.accountPublicId || '',
    name: row.name,
    role: row.role,
    phone: row.phone || '',
    email: row.email || '',
    department: row.department,
    title: row.title,
    managerEmployeeNo: manager?.employeeNo || '',
    entryDate: row.entryDate || '',
    employeeStatus: row.employeeStatus,
    active: row.active
  })
  const balances = await getData<LeaveBalance[]>(`/admin/employees/${row.id}/balances`)
  applyBalances(balances)
  visible.value = true
}

async function save() {
  if (!form.employeeNo.trim() || !form.name.trim() || !form.department || !form.title) {
    ElMessage.warning('请补全工号、姓名、部门和岗位')
    return
  }
  saving.value = true
  try {
    const saved = editingId.value
      ? await putData<Employee>(`/admin/employees/${editingId.value}`, form)
      : await postData<Employee>('/admin/employees', form)
    await putData(`/admin/employees/${saved.id}/balances`, {
      balances: balanceRows.map(({ leaveType, totalDays, usedDays }) => ({ leaveType, totalDays, usedDays }))
    })
    ElMessage.success('员工档案已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function openDetail(row: Employee) {
  detail.value = await getData<EmployeeDetail>(`/directory/employees/${row.id}`)
  detailVisible.value = true
}

async function resetPassword(row: Employee) {
  await ElMessageBox.confirm(`确认将 ${row.name} 的登录密码重置为 123456？`, '重置密码', {
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await postData(`/admin/employees/${row.id}/reset-password`)
  ElMessage.success('密码已重置为 123456')
}

onMounted(async () => {
  await load()
  const publicId = typeof route.query.bind === 'string' ? route.query.bind : ''
  const member = members.value.find((item) => item.publicId === publicId && item.status === 'PENDING_PROFILE')
  if (member) {
    const existing = employees.value.find((employee) => employee.accountPublicId === member.publicId)
    if (existing) {
      await openEdit(existing)
      applyMemberDraft(member)
      form.employeeStatus = 'ACTIVE'
      form.active = true
    } else {
      openCreate(member)
    }
    await router.replace({ path: '/employees' })
  }
})
</script>

<style scoped>
.page-actions,
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-row {
  margin-bottom: 12px;
}

.filter-row .el-input {
  width: 280px;
}

.filter-row .el-select {
  width: 180px;
}

.employee-form {
  margin-top: 14px;
}

.form-section-title {
  margin: 20px 0 10px;
  font-weight: 700;
  color: #172033;
}

.detail-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ec;
}

.detail-head h2,
.detail-head p {
  margin: 0;
}

.detail-head h2 {
  font-size: 19px;
}

.detail-head p {
  margin-top: 5px;
  color: #687386;
}

.detail-block {
  margin-top: 18px;
}

.request-detail {
  padding: 4px 18px;
  color: #475467;
}

.request-detail p {
  margin: 5px 0;
}

:deep(.employee-dialog) {
  max-width: calc(100vw - 32px);
}

@media (max-width: 700px) {
  .page-actions,
  .filter-row {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-row .el-input,
  .filter-row .el-select {
    width: 100%;
  }
}
</style>
