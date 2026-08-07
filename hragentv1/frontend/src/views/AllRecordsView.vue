<template>
  <div>
    <div class="page-title">
      <div>
        <h1>请假记录</h1>
        <p>HR 查看全部请假申请和完整审批状态。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <section class="content-panel">
      <el-table :data="requests" stripe>
        <el-table-column prop="employeeName" label="员工" width="100" />
        <el-table-column prop="managerName" label="主管" width="100" />
        <el-table-column prop="leaveTypeLabel" label="假别" width="90" />
        <el-table-column prop="startDate" label="开始" width="112" />
        <el-table-column prop="endDate" label="结束" width="112" />
        <el-table-column prop="days" label="天数" width="80" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <span :class="['status-pill', statusClass(row.status)]">{{ row.statusLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="aiRiskLevel" label="AI 风险" width="100" />
        <el-table-column prop="submittedAt" label="提交时间" min-width="160" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getData } from '../api/http'
import type { LeaveRequest } from '../api/types'

const requests = ref<LeaveRequest[]>([])

function statusClass(status: string) {
  if (status === 'APPROVED') return 'approved'
  if (status === 'REJECTED') return 'rejected'
  return 'pending'
}

async function load() {
  requests.value = await getData('/leave/all')
}

onMounted(load)
</script>
