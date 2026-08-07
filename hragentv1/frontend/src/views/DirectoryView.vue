<template>
  <div>
    <div class="page-title">
      <div>
        <h1>组织通讯录</h1>
        <p>按部门查看企业成员，点击员工可查看其档案与授权范围内的假期信息。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="directory-layout">
      <aside class="content-panel department-panel">
        <div class="panel-heading">部门</div>
        <button
          type="button"
          :class="['department-item', { active: selectedDepartment === 'ALL' }]"
          @click="selectedDepartment = 'ALL'"
        >
          <span>全部成员</span><strong>{{ overview.employees.length }}</strong>
        </button>
        <button
          v-for="department in overview.departments"
          :key="department.id"
          type="button"
          :class="['department-item', { active: selectedDepartment === department.name }]"
          @click="selectedDepartment = department.name"
        >
          <span>{{ department.name }}</span><strong>{{ department.memberCount }}</strong>
        </button>
      </aside>

      <section class="content-panel employee-panel">
        <div class="toolbar-row">
          <strong>{{ currentDepartmentLabel }}</strong>
          <el-input v-model="keyword" :prefix-icon="Search" clearable placeholder="搜索姓名、工号或岗位" />
        </div>
        <el-table :data="filteredEmployees" stripe highlight-current-row @row-click="openDetail">
          <el-table-column label="员工" min-width="160">
            <template #default="{ row }">
              <div class="employee-cell">
                <el-avatar :size="34">{{ row.name.slice(0, 1) }}</el-avatar>
                <div><strong>{{ row.name }}</strong><span>{{ row.employeeNo }}</span></div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="department" label="部门" min-width="130" />
          <el-table-column prop="title" label="岗位" min-width="140" />
          <el-table-column prop="managerName" label="直属主管" width="110" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="角色" width="110">
            <template #default="{ row }">{{ roleLabel(row.role) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>

    <el-drawer v-model="detailVisible" :size="drawerSize" title="员工详情">
      <template v-if="detail">
        <div class="detail-head">
          <el-avatar :size="52">{{ detail.employee.name.slice(0, 1) }}</el-avatar>
          <div>
            <h2>{{ detail.employee.name }}</h2>
            <p>{{ detail.employee.department }} · {{ detail.employee.title }}</p>
          </div>
        </div>
        <el-descriptions :column="2" border class="detail-section">
          <el-descriptions-item label="工号">{{ detail.employee.employeeNo }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleLabel(detail.employee.role) }}</el-descriptions-item>
          <el-descriptions-item label="直属主管">{{ detail.employee.managerName || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="入职日期">{{ detail.employee.entryDate || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="手机">{{ detail.employee.phone || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.employee.email || '未填写' }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="detail.leaveDataVisible">
          <div class="section-title">假期余额</div>
          <el-table :data="detail.balances" border size="small">
            <el-table-column prop="leaveTypeLabel" label="假别" />
            <el-table-column prop="totalDays" label="总额度" width="90" />
            <el-table-column prop="usedDays" label="已使用" width="90" />
            <el-table-column prop="remainingDays" label="剩余" width="90" />
          </el-table>
          <div class="section-title">请假记录</div>
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
        <el-alert
          v-else
          class="detail-section"
          type="info"
          :closable="false"
          title="可查看企业通讯录信息；假期余额和申请原因仅本人、直属主管及空间管理员可见。"
        />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getData } from '../api/http'
import type { DirectoryOverview, Employee, EmployeeDetail, Role } from '../api/types'

const overview = ref<DirectoryOverview>({ departments: [], employees: [] })
const selectedDepartment = ref('ALL')
const keyword = ref('')
const detail = ref<EmployeeDetail | null>(null)
const detailVisible = ref(false)
const drawerSize = window.innerWidth <= 760 ? '100%' : '720px'

const currentDepartmentLabel = computed(() => selectedDepartment.value === 'ALL' ? '全部成员' : selectedDepartment.value)
const filteredEmployees = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return overview.value.employees.filter((employee) => {
    const inDepartment = selectedDepartment.value === 'ALL' || employee.department === selectedDepartment.value
    const searchable = `${employee.name} ${employee.employeeNo} ${employee.title} ${employee.department}`.toLowerCase()
    return inDepartment && (!query || searchable.includes(query))
  })
})

function roleLabel(role: Role) {
  if (role === 'HR') return '空间管理员'
  if (role === 'MANAGER') return '主管'
  return '员工'
}

async function load() {
  overview.value = await getData<DirectoryOverview>('/directory')
}

async function openDetail(employee: Employee) {
  detail.value = await getData<EmployeeDetail>(`/directory/employees/${employee.id}`)
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.directory-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
}

.department-panel {
  align-self: start;
  padding: 10px;
}

.panel-heading {
  padding: 8px 10px 12px;
  font-weight: 700;
}

.department-item {
  width: 100%;
  min-height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #344054;
  cursor: pointer;
  text-align: left;
}

.department-item:hover,
.department-item.active {
  background: #eaf2fd;
  color: #175cd3;
}

.department-item strong {
  font-size: 12px;
}

.employee-panel .el-input {
  width: min(320px, 48%);
}

.employee-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.employee-cell strong,
.employee-cell span {
  display: block;
}

.employee-cell span {
  margin-top: 2px;
  color: #687386;
  font-size: 12px;
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

.detail-section {
  margin-top: 18px;
}

.section-title {
  margin: 22px 0 10px;
  font-weight: 700;
}

.request-detail {
  padding: 4px 18px;
  color: #475467;
}

.request-detail p {
  margin: 5px 0;
}

@media (max-width: 760px) {
  .directory-layout {
    grid-template-columns: 1fr;
  }

  .department-panel {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panel-heading {
    grid-column: 1 / -1;
  }

  .employee-panel .el-input {
    width: 100%;
  }

  .toolbar-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
