<template>
  <div>
    <div class="page-title">
      <div>
        <h1>{{ isHr ? '个人信息库' : '个人信息' }}</h1>
        <p>{{ myProfile ? `${myProfile.employeeNo} · ${myProfile.department} · ${myProfile.title}` : '正在加载员工档案' }}</p>
      </div>
      <el-tooltip content="刷新" placement="bottom">
        <el-button :icon="Refresh" circle :loading="loading" aria-label="刷新" @click="load" />
      </el-tooltip>
    </div>

    <el-tabs v-if="isHr" v-model="activeTab" class="profile-tabs">
      <el-tab-pane label="我的信息" name="mine" />
      <el-tab-pane label="员工信息库" name="directory" />
    </el-tabs>

    <template v-if="activeTab === 'mine'">
      <section v-if="myProfile" class="content-panel profile-header">
        <el-avatar :size="58">{{ (myProfile.legalName || myProfile.displayName).slice(0, 1) }}</el-avatar>
        <div class="profile-heading">
          <div class="heading-line">
            <h2>{{ myProfile.legalName || myProfile.displayName }}</h2>
            <el-tag :type="myProfile.employeeStatus === 'ACTIVE' ? 'success' : 'info'">
              {{ statusLabel(myProfile.employeeStatus) }}
            </el-tag>
          </div>
          <p>{{ myProfile.englishName || myProfile.displayName }} · {{ roleLabel(myProfile.role) }}</p>
        </div>
        <div class="profile-number">
          <span>员工编号</span>
          <strong>{{ myProfile.employeeNo }}</strong>
        </div>
      </section>

      <section v-if="myProfile" class="content-panel info-section">
        <div class="section-title"><h3>任职信息</h3></div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="所属部门">{{ valueOf(myProfile.department) }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ valueOf(myProfile.title) }}</el-descriptions-item>
          <el-descriptions-item label="直属主管">{{ valueOf(myProfile.managerName) }}</el-descriptions-item>
          <el-descriptions-item label="入职日期">{{ valueOf(myProfile.entryDate) }}</el-descriptions-item>
          <el-descriptions-item label="用工类型">{{ valueOf(myProfile.employmentType) }}</el-descriptions-item>
          <el-descriptions-item label="工作地点">{{ valueOf(myProfile.workLocation) }}</el-descriptions-item>
          <el-descriptions-item label="合同开始">{{ valueOf(myProfile.contractStartDate) }}</el-descriptions-item>
          <el-descriptions-item label="合同结束">{{ valueOf(myProfile.contractEndDate) }}</el-descriptions-item>
          <el-descriptions-item label="税前月薪">{{ formatSalary(myProfile) }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section v-if="myProfile" class="content-panel info-section">
        <div class="section-title">
          <h3>身份与证件</h3>
          <el-tooltip :content="showSensitive ? '隐藏敏感信息' : '显示敏感信息'" placement="left">
            <el-button
              :icon="showSensitive ? Hide : View"
              circle
              aria-label="切换敏感信息显示"
              @click="showSensitive = !showSensitive"
            />
          </el-tooltip>
        </div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="法定姓名">{{ valueOf(myProfile.legalName) }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ valueOf(myProfile.gender) }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ valueOf(myProfile.birthDate) }}</el-descriptions-item>
          <el-descriptions-item label="国籍">{{ valueOf(myProfile.nationality) }}</el-descriptions-item>
          <el-descriptions-item label="证件类型">{{ valueOf(myProfile.idType) }}</el-descriptions-item>
          <el-descriptions-item label="证件号码">{{ sensitiveValue(myProfile.idNumber) }}</el-descriptions-item>
          <el-descriptions-item label="护照号码">{{ sensitiveValue(myProfile.passportNumber) }}</el-descriptions-item>
          <el-descriptions-item label="护照有效期">{{ valueOf(myProfile.passportExpiryDate) }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section v-if="myProfile" class="content-panel info-section">
        <div class="section-title"><h3>联系信息</h3></div>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="手机号码">{{ valueOf(myProfile.phone) }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ valueOf(myProfile.email) }}</el-descriptions-item>
          <el-descriptions-item label="家庭住址">{{ valueOf(myProfile.homeAddress) }}</el-descriptions-item>
          <el-descriptions-item label="紧急联系人">{{ valueOf(myProfile.emergencyContactName) }}</el-descriptions-item>
          <el-descriptions-item label="紧急联系电话">{{ valueOf(myProfile.emergencyContactPhone) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(myProfile.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </section>
    </template>

    <section v-else class="content-panel directory-panel">
      <div class="toolbar-row">
        <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索姓名、工号、部门或岗位" />
        <span class="muted">{{ filteredProfiles.length }} 条档案</span>
      </div>
      <el-table :data="filteredProfiles" stripe highlight-current-row @row-click="openProfile">
        <el-table-column prop="employeeNo" label="工号" width="100" />
        <el-table-column label="姓名" min-width="130">
          <template #default="{ row }">
            <strong>{{ row.legalName || row.displayName }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" min-width="130" />
        <el-table-column prop="title" label="岗位" min-width="150" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">{{ roleLabel(row.role) }}</template>
        </el-table-column>
        <el-table-column prop="employmentType" label="用工类型" width="110">
          <template #default="{ row }">{{ valueOf(row.employmentType) }}</template>
        </el-table-column>
        <el-table-column label="档案状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.maintained ? 'success' : 'warning'">
              {{ row.maintained ? '已维护' : '待维护' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="编辑档案" placement="left">
              <el-button :icon="Edit" circle aria-label="编辑档案" @click.stop="openProfile(row)" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="drawerVisible" :size="drawerSize" :title="`个人档案 · ${editingName}`">
      <el-form :model="form" label-position="top" class="profile-form">
        <div class="form-section-title">身份信息</div>
        <div class="form-grid">
          <el-form-item label="法定姓名"><el-input v-model="form.legalName" /></el-form-item>
          <el-form-item label="英文姓名"><el-input v-model="form.englishName" /></el-form-item>
          <el-form-item label="性别">
            <el-select v-model="form.gender" clearable style="width: 100%">
              <el-option label="男" value="男" />
              <el-option label="女" value="女" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="出生日期">
            <el-date-picker v-model="form.birthDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="国籍"><el-input v-model="form.nationality" /></el-form-item>
          <el-form-item label="证件类型"><el-input v-model="form.idType" /></el-form-item>
          <el-form-item label="证件号码"><el-input v-model="form.idNumber" /></el-form-item>
          <el-form-item label="护照号码"><el-input v-model="form.passportNumber" /></el-form-item>
          <el-form-item label="护照有效期">
            <el-date-picker v-model="form.passportExpiryDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="form-section-title">任职与合同</div>
        <div class="form-grid">
          <el-form-item label="用工类型">
            <el-select v-model="form.employmentType" clearable style="width: 100%">
              <el-option label="全日制" value="全日制" />
              <el-option label="非全日制" value="非全日制" />
              <el-option label="实习" value="实习" />
              <el-option label="劳务派遣" value="劳务派遣" />
            </el-select>
          </el-form-item>
          <el-form-item label="工作地点"><el-input v-model="form.workLocation" /></el-form-item>
          <el-form-item label="合同开始日期">
            <el-date-picker v-model="form.contractStartDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="合同结束日期">
            <el-date-picker v-model="form.contractEndDate" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="税前月薪">
            <el-input-number v-model="form.monthlySalary" :min="0" :step="1000" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="币种">
            <el-select v-model="form.currency" clearable style="width: 100%">
              <el-option label="人民币 CNY" value="CNY" />
              <el-option label="美元 USD" value="USD" />
              <el-option label="欧元 EUR" value="EUR" />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-section-title">联系信息</div>
        <div class="form-grid">
          <el-form-item label="家庭住址" class="full-width"><el-input v-model="form.homeAddress" /></el-form-item>
          <el-form-item label="紧急联系人"><el-input v-model="form.emergencyContactName" /></el-form-item>
          <el-form-item label="紧急联系电话"><el-input v-model="form.emergencyContactPhone" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存档案</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { Edit, Hide, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getData, putData } from '../api/http'
import type {
  EmployeePersonalProfile,
  EmployeePersonalProfileSummary,
  EmployeePersonalProfileUpdate,
  Role
} from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isHr = computed(() => auth.user?.role === 'HR')
const activeTab = ref(isHr.value ? 'directory' : 'mine')
const loading = ref(false)
const saving = ref(false)
const showSensitive = ref(false)
const myProfile = ref<EmployeePersonalProfile | null>(null)
const profiles = ref<EmployeePersonalProfileSummary[]>([])
const keyword = ref('')
const drawerVisible = ref(false)
const editingEmployeeId = ref<number | null>(null)
const editingName = ref('')
const drawerSize = window.innerWidth <= 760 ? '100%' : '760px'

function emptyForm(): EmployeePersonalProfileUpdate {
  return {
    legalName: '',
    englishName: '',
    gender: '',
    birthDate: '',
    nationality: '',
    idType: '',
    idNumber: '',
    passportNumber: '',
    passportExpiryDate: '',
    employmentType: '',
    contractStartDate: '',
    contractEndDate: '',
    workLocation: '',
    monthlySalary: undefined,
    currency: 'CNY',
    homeAddress: '',
    emergencyContactName: '',
    emergencyContactPhone: ''
  }
}

const form = reactive<EmployeePersonalProfileUpdate>(emptyForm())

const filteredProfiles = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return profiles.value
  return profiles.value.filter((profile) =>
    `${profile.employeeNo} ${profile.displayName} ${profile.legalName || ''} ${profile.department} ${profile.title}`
      .toLowerCase()
      .includes(query)
  )
})

async function load() {
  loading.value = true
  try {
    myProfile.value = await getData<EmployeePersonalProfile>('/personal-profiles/me')
    if (isHr.value) {
      profiles.value = await getData<EmployeePersonalProfileSummary[]>('/personal-profiles')
    }
  } finally {
    loading.value = false
  }
}

async function openProfile(summary: EmployeePersonalProfileSummary) {
  const detail = await getData<EmployeePersonalProfile>(`/personal-profiles/${summary.employeeId}`)
  editingEmployeeId.value = summary.employeeId
  editingName.value = detail.legalName || detail.displayName
  Object.assign(form, emptyForm(), {
    legalName: detail.legalName || '',
    englishName: detail.englishName || '',
    gender: detail.gender || '',
    birthDate: detail.birthDate || '',
    nationality: detail.nationality || '',
    idType: detail.idType || '',
    idNumber: detail.idNumber || '',
    passportNumber: detail.passportNumber || '',
    passportExpiryDate: detail.passportExpiryDate || '',
    employmentType: detail.employmentType || '',
    contractStartDate: detail.contractStartDate || '',
    contractEndDate: detail.contractEndDate || '',
    workLocation: detail.workLocation || '',
    monthlySalary: detail.monthlySalary,
    currency: detail.currency || 'CNY',
    homeAddress: detail.homeAddress || '',
    emergencyContactName: detail.emergencyContactName || '',
    emergencyContactPhone: detail.emergencyContactPhone || ''
  })
  drawerVisible.value = true
}

async function saveProfile() {
  if (!editingEmployeeId.value) return
  if (form.contractStartDate && form.contractEndDate && form.contractEndDate < form.contractStartDate) {
    ElMessage.warning('合同结束日期不能早于开始日期')
    return
  }
  saving.value = true
  try {
    await putData(`/personal-profiles/${editingEmployeeId.value}`, form)
    ElMessage.success('个人档案已保存')
    drawerVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function valueOf(value?: string | null) {
  return value || '未维护'
}

function sensitiveValue(value?: string) {
  if (!value) return '未维护'
  if (showSensitive.value || value.length <= 6) return value
  return `${value.slice(0, 3)}${'*'.repeat(Math.min(8, value.length - 5))}${value.slice(-2)}`
}

function roleLabel(role: Role) {
  if (role === 'HR') return '空间管理员'
  if (role === 'MANAGER') return '主管'
  return '员工'
}

function statusLabel(status?: string) {
  if (status === 'LEFT') return '离职'
  if (status === 'INACTIVE') return '停用'
  return '在职'
}

function formatSalary(profile: EmployeePersonalProfile) {
  if (profile.monthlySalary === undefined || profile.monthlySalary === null) return '未维护'
  return `${profile.currency || 'CNY'} ${Number(profile.monthlySalary).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`
}

function formatDateTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '未维护'
}

onMounted(load)
</script>

<style scoped>
.profile-tabs {
  margin-bottom: 14px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-heading {
  min-width: 0;
  flex: 1;
}

.heading-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.heading-line h2,
.profile-heading p {
  margin: 0;
}

.heading-line h2 {
  font-size: 19px;
}

.profile-heading p {
  margin-top: 5px;
  color: #687386;
  font-size: 13px;
}

.profile-number {
  min-width: 120px;
  text-align: right;
}

.profile-number span,
.profile-number strong {
  display: block;
}

.profile-number span {
  color: #687386;
  font-size: 12px;
}

.profile-number strong {
  margin-top: 4px;
  font-size: 16px;
}

.info-section {
  margin-top: 14px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 12px;
}

.section-title h3,
.form-section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
}

.directory-panel .el-input {
  width: 360px;
}

.profile-form {
  padding: 0 4px 24px;
}

.form-section-title {
  margin: 8px 0 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ec;
}

.form-section-title:not(:first-child) {
  margin-top: 24px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.full-width {
  grid-column: 1 / -1;
}

@media (max-width: 760px) {
  .profile-header {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .profile-number {
    width: 100%;
    text-align: left;
  }

  .directory-panel .el-input {
    width: 100%;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .full-width {
    grid-column: auto;
  }
}
</style>
