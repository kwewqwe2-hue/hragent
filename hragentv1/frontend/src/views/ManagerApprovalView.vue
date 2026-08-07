<template>
  <div>
    <div class="page-title">
      <div>
        <h1>主管审批</h1>
        <p>查看下属提交的请假申请和智能体判断依据，决定通过或驳回。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <section class="content-panel">
      <el-table :data="requests" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-box">
              <p><strong>请假原因：</strong>{{ row.reason }}</p>
              <p><strong>AI 风险：</strong>{{ row.aiRiskLevel }}</p>
              <p><strong>AI 摘要：</strong>{{ row.aiSummary }}</p>
              <p><strong>知识库依据：</strong>{{ row.aiEvidence }}</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="employeeName" label="员工" width="100" />
        <el-table-column prop="leaveTypeLabel" label="假别" width="90" />
        <el-table-column prop="startDate" label="开始" width="112" />
        <el-table-column prop="endDate" label="结束" width="112" />
        <el-table-column prop="days" label="天数" width="80" />
        <el-table-column prop="aiRiskLevel" label="AI 风险" width="100" />
        <el-table-column prop="submittedAt" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" :icon="Check" @click="review(row.id, true)">通过</el-button>
            <el-button type="danger" size="small" :icon="Close" @click="review(row.id, false)">驳回</el-button>
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

const requests = ref<LeaveRequest[]>([])

async function load() {
  requests.value = await getData('/leave/manager/pending')
}

async function review(id: number, approved: boolean) {
  const { value } = await ElMessageBox.prompt('请输入审批意见', approved ? '审批通过' : '审批驳回', {
    inputValue: approved ? '同意，请 HR 备案。' : '申请信息不足，暂不通过。',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await putData(`/leave/manager/${id}/review`, { approved, opinion: value })
  ElMessage.success('处理成功')
  await load()
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
