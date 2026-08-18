<template>
  <div>
    <div class="page-title">
      <div>
        <h1>HR 备案</h1>
        <p>主管通过后进入 HR 备案。备案通过时系统会扣减员工假期余额。</p>
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
              <p><strong>主管意见：</strong>{{ row.managerOpinion }}</p>
              <p><strong>知识库依据：</strong>{{ row.aiEvidence }}</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="employeeName" label="员工" width="100" />
        <el-table-column prop="managerName" label="主管" width="100" />
        <el-table-column prop="leaveTypeLabel" label="假别" width="90" />
        <el-table-column prop="startDate" label="开始" width="112" />
        <el-table-column prop="endDate" label="结束" width="112" />
        <el-table-column prop="days" label="天数" width="80" />
        <el-table-column prop="managerReviewedAt" label="主管审批时间" min-width="160" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button type="success" size="small" :icon="Check" @click="record(row.id, true)">备案通过</el-button>
            <el-button type="danger" size="small" :icon="Close" @click="record(row.id, false)">驳回</el-button>
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
  requests.value = await getData('/leave/hr/pending')
}

async function record(id: number, approved: boolean) {
  const { value } = await ElMessageBox.prompt('请输入 HR 备案意见', approved ? '备案通过' : '备案驳回', {
    inputValue: approved ? '备案通过，系统扣减假期余额。' : '备案不通过，请员工补充材料后重新提交。',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  })
  await putData(`/leave/hr/${id}/record`, { approved, opinion: value })
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
