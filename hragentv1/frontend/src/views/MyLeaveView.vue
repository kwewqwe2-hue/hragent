<template>
  <div>
    <div class="page-title">
      <div>
        <h1>我的请假</h1>
        <p>提交请假申请，系统会先校验余额并生成智能体辅助判断。</p>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="9">
        <section class="content-panel">
          <div class="toolbar-row">
            <strong>发起申请</strong>
          </div>
          <el-alert
            class="workday-alert"
            type="info"
            :closable="false"
            title="系统按周一至周五自动计算请假天数，周六、周日不扣假期余额。"
          />
          <el-form :model="form" label-position="top">
            <el-form-item label="请假类型">
              <el-select v-model="form.leaveType" placeholder="选择假别" style="width: 100%">
                <el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期范围">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="计费天数（系统自动计算）">
              <el-input-number v-model="form.days" :min="0" :step="1" disabled style="width: 100%" />
            </el-form-item>
            <el-form-item label="请假原因">
              <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="600" show-word-limit />
            </el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
          </el-form>
        </section>
      </el-col>

      <el-col :xs="24" :lg="15">
        <section class="content-panel">
          <div class="toolbar-row">
            <strong>申请记录</strong>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
          <el-table :data="requests" stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-box">
                  <p><strong>原因：</strong>{{ row.reason }}</p>
                  <p><strong>AI 风险：</strong>{{ row.aiRiskLevel }}</p>
                  <p><strong>AI 摘要：</strong>{{ row.aiSummary }}</p>
                  <p><strong>依据：</strong>{{ row.aiEvidence }}</p>
                  <p v-if="row.managerOpinion"><strong>主管意见：</strong>{{ row.managerOpinion }}</p>
                  <p v-if="row.hrOpinion"><strong>HR 意见：</strong>{{ row.hrOpinion }}</p>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="leaveTypeLabel" label="假别" width="90" />
            <el-table-column prop="startDate" label="开始" width="112" />
            <el-table-column prop="endDate" label="结束" width="112" />
            <el-table-column prop="days" label="天数" width="80" />
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <span :class="['status-pill', statusClass(row.status)]">{{ row.statusLabel }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="submittedAt" label="提交时间" min-width="160" />
          </el-table>
        </section>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getData, postData } from '../api/http'
import type { LeaveRequest, LeaveType } from '../api/types'

const types = ref<{ value: LeaveType; label: string }[]>([])
const requests = ref<LeaveRequest[]>([])
const dateRange = ref<[string, string] | null>(null)
const submitting = ref(false)
const form = reactive({
  leaveType: 'ANNUAL' as LeaveType,
  startDate: '',
  endDate: '',
  days: 1,
  reason: ''
})

watch(dateRange, (value) => {
  form.startDate = value?.[0] || ''
  form.endDate = value?.[1] || ''
  if (value) {
    const workingDays = countWorkingDays(value[0], value[1])
    form.days = workingDays
  }
})

function countWorkingDays(start: string, end: string) {
  const cursor = new Date(`${start}T00:00:00`)
  const last = new Date(`${end}T00:00:00`)
  let count = 0
  while (cursor <= last) {
    const day = cursor.getDay()
    if (day !== 0 && day !== 6) count += 1
    cursor.setDate(cursor.getDate() + 1)
  }
  return count
}

function statusClass(status: string) {
  if (status === 'APPROVED') return 'approved'
  if (status === 'REJECTED') return 'rejected'
  return 'pending'
}

async function load() {
  types.value = await getData('/leave/types')
  requests.value = await getData('/leave/my')
}

async function submit() {
  if (!form.startDate || !form.endDate || !form.reason.trim()) {
    ElMessage.warning('请补全请假类型、日期、天数和原因')
    return
  }
  submitting.value = true
  try {
    await postData('/leave', form)
    ElMessage.success('申请已提交')
    form.reason = ''
    await load()
  } finally {
    submitting.value = false
  }
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

.workday-alert {
  margin-bottom: 14px;
}
</style>
