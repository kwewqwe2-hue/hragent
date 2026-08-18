<template>
  <div>
    <div class="page-title">
      <div>
        <h1>数据导入</h1>
        <p>上传 Excel/CSV，先预览校验，确认后再写入数据库。</p>
      </div>
      <el-button :icon="Refresh" @click="loadBatches">刷新记录</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="9">
        <section class="content-panel">
          <div class="toolbar-row">
            <strong>上传文件</strong>
            <el-dropdown @command="downloadTemplate">
              <el-button :icon="Download">下载模板</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="employees">员工模板</el-dropdown-item>
                  <el-dropdown-item command="balances">余额模板</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <el-form label-position="top">
            <el-form-item label="导入类型">
              <el-radio-group v-model="importType">
                <el-radio-button label="employees">员工数据</el-radio-button>
                <el-radio-button label="balances">假期余额</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="文件">
              <input ref="fileInput" type="file" accept=".xlsx,.csv" @change="pickFile" />
            </el-form-item>
            <el-alert type="info" :closable="false" show-icon>
              <template #title>{{ helpText }}</template>
            </el-alert>
            <div class="actions">
              <el-button :icon="View" :loading="loading" @click="preview">预览校验</el-button>
              <el-button type="primary" :icon="Upload" :disabled="!canCommit" :loading="loading" @click="commit">
                确认导入
              </el-button>
            </div>
          </el-form>
        </section>
      </el-col>
      <el-col :xs="24" :lg="15">
        <section class="content-panel">
          <div class="toolbar-row">
            <strong>校验结果</strong>
            <span v-if="result" class="muted">
              共 {{ result.totalRows }} 行，可导入 {{ result.validRows }} 行，错误 {{ result.failedRows }} 行
            </span>
          </div>
          <el-table :data="result?.rows || []" stripe>
            <el-table-column prop="rowNumber" label="行号" width="80" />
            <el-table-column prop="action" label="动作" width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.valid ? 'success' : 'danger'">{{ row.valid ? '通过' : '错误' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="提示" min-width="220" />
            <el-table-column label="原始数据" min-width="260">
              <template #default="{ row }">{{ row.values.join(' | ') }}</template>
            </el-table-column>
          </el-table>
        </section>
      </el-col>
    </el-row>

    <section class="content-panel manual-panel">
      <div class="toolbar-row">
        <div>
          <strong>单人建档与平台内更新</strong>
          <p class="manual-copy">不使用文件时，可选择一名已审核成员，直接填写员工档案、组织关系和假期额度。</p>
        </div>
        <div class="manual-actions">
          <el-select v-model="selectedPublicId" clearable filterable placeholder="选择待建档成员">
            <el-option
              v-for="member in pendingMembers"
              :key="member.publicId"
              :label="`${member.name} / ${member.publicId}`"
              :value="member.publicId"
            />
          </el-select>
          <el-button type="primary" :icon="User" @click="openManualProfile">
            {{ selectedPublicId ? '为该成员建档' : '进入员工数据' }}
          </el-button>
        </div>
      </div>
    </section>

    <section class="content-panel history">
      <div class="toolbar-row">
        <strong>导入记录</strong>
      </div>
      <el-table :data="batches" stripe>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="importType" label="类型" width="110" />
        <el-table-column prop="fileName" label="文件" min-width="180" />
        <el-table-column prop="totalRows" label="总行数" width="90" />
        <el-table-column prop="successRows" label="成功" width="90" />
        <el-table-column prop="failedRows" label="失败" width="90" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column prop="message" label="说明" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Download, Refresh, Upload, User, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getData, http } from '../api/http'
import type { ApiResponse, ImportBatch, ImportResult, WorkspaceMember } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const importType = ref<'employees' | 'balances'>('employees')
const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const result = ref<ImportResult | null>(null)
const batches = ref<ImportBatch[]>([])
const members = ref<WorkspaceMember[]>([])
const selectedPublicId = ref('')
const pendingMembers = computed(() => members.value.filter((member) => member.status === 'PENDING_PROFILE'))

const helpText = computed(() => {
  if (importType.value === 'employees') {
    return '员工表头：userId（注册账号ID，可选）, employeeNo, name, role, phone, email, department, title, managerEmployeeNo, entryDate, status'
  }
  return '余额表头：employeeNo, annualBalance, sickBalance, personalBalance, marriageBalance；也支持 annualTotal/annualUsed'
})

const canCommit = computed(() => Boolean(file.value && result.value && result.value.failedRows === 0 && result.value.totalRows > 0))

function pickFile(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] || null
  result.value = null
}

async function upload(commit: boolean) {
  if (!file.value) {
    ElMessage.warning('请先选择 .xlsx 或 .csv 文件')
    return
  }
  loading.value = true
  try {
    const form = new FormData()
    form.append('file', file.value)
    const response = await http.post<ApiResponse<ImportResult>>(`/admin/import/${importType.value}?commit=${commit}`, form)
    result.value = response.data.data
    if (commit && result.value.committed) {
      ElMessage.success('导入完成')
      if (fileInput.value) fileInput.value.value = ''
      file.value = null
      await loadBatches()
    } else if (commit) {
      ElMessage.warning('仍有错误行，未写入数据')
    }
  } finally {
    loading.value = false
  }
}

function preview() {
  upload(false)
}

function commit() {
  upload(true)
}

async function loadBatches() {
  batches.value = await getData('/admin/imports')
}

async function loadMembers() {
  if (!auth.user?.tenantId) return
  members.value = await getData<WorkspaceMember[]>(`/workspaces/${auth.user.tenantId}/members`)
}

function openManualProfile() {
  router.push(selectedPublicId.value
    ? { path: '/employees', query: { bind: selectedPublicId.value } }
    : { path: '/employees' })
}

function downloadTemplate(command: string) {
  const employeeTemplate = [
    'userId,employeeNo,name,role,phone,email,department,title,managerEmployeeNo,entryDate,status',
    'USR-XXXXXXXXXX,E001,张三,EMPLOYEE,13800000000,zhangsan@example.com,研发部,Java工程师,M001,2026-01-01,ACTIVE'
  ].join('\r\n')
  const balanceTemplate = [
    'employeeNo,annualTotal,annualUsed,sickTotal,sickUsed,personalTotal,personalUsed,marriageTotal,marriageUsed',
    'E001,5,0,10,0,5,0,10,0'
  ].join('\r\n')
  const content = command === 'balances' ? balanceTemplate : employeeTemplate
  const fileName = command === 'balances' ? '假期余额导入模板.csv' : '员工数据导入模板.csv'
  const blob = new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  await Promise.all([loadBatches(), loadMembers()])
})
</script>

<style scoped>
.actions {
  margin-top: 16px;
  display: flex;
  gap: 10px;
}

.history {
  margin-top: 16px;
}

.manual-panel {
  margin-top: 16px;
}

.manual-copy {
  margin: 5px 0 0;
  color: #687386;
  font-size: 13px;
}

.manual-actions {
  display: flex;
  gap: 10px;
}

.manual-actions .el-select {
  width: 300px;
}

@media (max-width: 760px) {
  .manual-panel .toolbar-row,
  .manual-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .manual-actions .el-select {
    width: 100%;
  }
}
</style>
