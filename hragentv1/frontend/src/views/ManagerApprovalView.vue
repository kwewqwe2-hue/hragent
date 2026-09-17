<template>
  <div>
    <div class="page-title">
      <div>
        <h1>主管审批</h1>
        <p>请依据申请事实和制度资料处理；AI 风险仅供辅助，不替代主管判断。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <section class="content-panel">
      <el-table v-loading="loading" :data="requests" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-box">
              <p><strong>请假原因：</strong>{{ row.reason }}</p>
              <p><strong>AI 风险：</strong>{{ row.aiRiskLevel }}</p>
              <p><strong>AI 摘要：</strong>{{ row.aiSummary }}</p>
              <p><strong>知识库依据：</strong>{{ row.aiEvidence }}</p>
              <LeaveMedicalReview v-if="row.medicalRecordId" :id="row.id" reviewer />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="employeeName" label="员工" width="100" />
        <el-table-column prop="leaveTypeLabel" label="假别" width="90" />
        <el-table-column prop="startDate" label="开始" width="112" />
        <el-table-column prop="endDate" label="结束" width="112" />
        <el-table-column prop="days" label="天数" width="80" />
        <el-table-column label="AI 风险" width="100">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.aiRiskLevel)" effect="plain">{{ row.aiRiskLevel || '待确认' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              type="success"
              size="small"
              :icon="Check"
              :loading="reviewingId === row.id && reviewingApproved"
              :disabled="reviewingId !== null"
              @click="review(row.id, true)"
            >通过</el-button>
            <el-button
              type="danger"
              size="small"
              :icon="Close"
              :loading="reviewingId === row.id && !reviewingApproved"
              :disabled="reviewingId !== null"
              @click="review(row.id, false)"
            >驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Check, Close, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getData, putData } from '../api/http'
import type { LeaveRequest } from '../api/types'
import LeaveMedicalReview from '../components/LeaveMedicalReview.vue'

const requests = ref<LeaveRequest[]>([])
const loading = ref(false)
const reviewingId = ref<number | null>(null)
const reviewingApproved = ref(false)

async function load() {
  loading.value = true
  try {
    requests.value = await getData('/leave/manager/pending')
  } finally {
    loading.value = false
  }
}

async function review(id: number, approved: boolean) {
  if (reviewingId.value !== null) return
  try {
    const { value } = await ElMessageBox.prompt(
      approved
        ? '通过后，申请将进入 HR 备案阶段，余额不会在此时扣减。请填写审批意见。'
        : '驳回后申请将结束。请填写员工可执行的补充或修改建议。',
      approved ? '确认通过申请' : '确认驳回申请',
      {
        inputValue: approved ? '同意，提交 HR 备案。' : '申请信息需要补充，请修改后重新提交。',
        inputPlaceholder: '最多 600 字',
        inputValidator: (value) => value.trim() ? true : '请填写审批意见，方便员工理解下一步操作。',
        confirmButtonText: approved ? '确认通过' : '确认驳回',
        cancelButtonText: '取消'
      }
    )
    reviewingId.value = id
    reviewingApproved.value = approved
    const result = await putData<LeaveRequest>(`/leave/manager/${id}/review`, { approved, opinion: value.trim() })
    ElMessage.success(approved
      ? `申请已通过，现为“${result.statusLabel}”`
      : `申请已驳回，员工将收到处理结果`)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') throw error
  } finally {
    reviewingId.value = null
    reviewingApproved.value = false
  }
}

function riskTagType(risk: string) {
  if (risk === '高') return 'danger'
  if (risk === '中') return 'warning'
  if (risk === '低') return 'success'
  return 'info'
}

onMounted(load)
</script>

<style scoped>
.expand-box {
  padding: 8px 22px;
  color: #344054;
  line-height: 1.7;
}

.expand-box p {
  margin: 6px 0;
}
</style>
