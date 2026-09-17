<template>
  <div>
    <div class="page-title">
      <div>
        <h1>我的请假</h1>
        <p>先核验实际工作日、可用余额和审批人，再确认提交申请。</p>
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
              title="工作日、余额和重复日期均由服务端规则核验；页面估算不作为最终结果。"
            />
          <el-form :model="form" label-position="top">
            <el-form-item label="请假类型">
              <el-select v-model="form.leaveType" placeholder="选择假别" style="width: 100%" :disabled="submitting">
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
                :disabled="submitting"
              />
            </el-form-item>
            <el-form-item label="计费天数（以核验结果为准）">
              <el-input-number v-model="form.days" :min="0" :step="1" disabled style="width: 100%" />
            </el-form-item>
            <el-form-item label="请假原因">
              <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="600" show-word-limit :disabled="submitting" />
            </el-form-item>
            <LeaveMedicalUpload v-if="form.leaveType === 'SICK'" :start-date="form.startDate" :end-date="form.endDate" :disabled="submitting" @ready="form.medicalRecordId = $event" @busy="medicalBusy = $event" />
            <div v-if="preview" class="preview-card">
              <div class="preview-heading">
                <strong>提交前核验通过</strong>
                <span>服务端实时结果</span>
              </div>
              <dl>
                <div><dt>实际工作日</dt><dd>{{ preview.workingDays }} 天</dd></div>
                <div><dt>可用余额</dt><dd>{{ preview.availableDaysBefore }} 天 -> {{ preview.availableDaysAfter }} 天</dd></div>
                <div><dt>审批人</dt><dd>{{ preview.managerName }}（{{ preview.managerEmployeeNo }}）</dd></div>
              </dl>
              <p>确认提交后，申请将进入审批流程；最终天数和余额以审批备案结果为准。</p>
            </div>
            <div class="form-actions">
              <el-button :loading="previewing" :disabled="submitting" @click="previewLeave">核验申请</el-button>
              <el-button type="primary" :disabled="!preview || previewing || medicalBusy || (form.leaveType === 'SICK' && !form.medicalRecordId)" :loading="submitting" @click="submit">确认提交</el-button>
            </div>
          </el-form>
        </section>
      </el-col>

      <el-col :xs="24" :lg="15">
        <section class="content-panel">
          <div class="toolbar-row">
            <strong>申请记录</strong>
            <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
          </div>
          <el-table v-loading="loading" :data="requests" stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-box">
                  <p><strong>原因：</strong>{{ row.reason }}</p>
                  <p><strong>AI 风险：</strong>{{ row.aiRiskLevel }}</p>
                  <p><strong>AI 摘要：</strong>{{ row.aiSummary }}</p>
                  <p><strong>依据：</strong>{{ row.aiEvidence }}</p>
                  <p v-if="row.managerOpinion"><strong>主管意见：</strong>{{ row.managerOpinion }}</p>
                  <p v-if="row.hrOpinion"><strong>HR 意见：</strong>{{ row.hrOpinion }}</p>
                  <LeaveMedicalReview v-if="row.medicalRecordId" :id="row.id" />
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getData, postData } from '../api/http'
import type { LeavePreview, LeaveRequest, LeaveType } from '../api/types'
import LeaveMedicalUpload from '../components/LeaveMedicalUpload.vue'
import LeaveMedicalReview from '../components/LeaveMedicalReview.vue'
const medicalBusy = ref(false)

const types = ref<{ value: LeaveType; label: string }[]>([])
const requests = ref<LeaveRequest[]>([])
const dateRange = ref<[string, string] | null>(null)
const submitting = ref(false)
const previewing = ref(false)
const loading = ref(false)
const preview = ref<LeavePreview | null>(null)
const previewFingerprint = ref('')
const form = reactive({
  medicalRecordId: null as number | null,
  leaveType: 'ANNUAL' as LeaveType,
  startDate: '',
  endDate: '',
  days: 1,
  reason: ''
})

watch(dateRange, (value) => {
  form.startDate = value?.[0] || ''
  form.endDate = value?.[1] || ''
  // The backend recomputes this field. Keep a valid placeholder until the
  // preview response supplies the authoritative working-day count.
  form.days = 1
})

watch(()=>form.leaveType,()=>{form.medicalRecordId=null})
const fingerprint = computed(() => [form.leaveType, form.startDate, form.endDate, form.reason.trim(), form.medicalRecordId].join('|'))

watch(fingerprint, () => {
  preview.value = null
  previewFingerprint.value = ''
})

function statusClass(status: string) {
  if (status === 'APPROVED') return 'approved'
  if (status === 'REJECTED') return 'rejected'
  return 'pending'
}

async function load() {
  loading.value = true
  try {
    const [leaveTypes, leaveRequests] = await Promise.all([
      getData<{ value: LeaveType; label: string }[]>('/leave/types'),
      getData<LeaveRequest[]>('/leave/my')
    ])
    types.value = leaveTypes
    requests.value = leaveRequests
  } finally {
    loading.value = false
  }
}

function validateDraft() {
  if (!form.startDate || !form.endDate || !form.reason.trim()) {
    ElMessage.warning('请补全请假类型、日期和原因后再核验')
    return false
  }
  if(form.leaveType === 'SICK' && !form.medicalRecordId){ElMessage.warning('请先上传病假材料完成初检');return false}
  return true
}

async function previewLeave() {
  if (!validateDraft()) return
  previewing.value = true
  try {
    const result = await postData<LeavePreview>('/leave/preview', form)
    preview.value = result
    previewFingerprint.value = fingerprint.value
    form.days = result.workingDays
  } finally {
    previewing.value = false
  }
}

async function submit() {
  if (!validateDraft()) return
  if (!preview.value || previewFingerprint.value !== fingerprint.value) {
    ElMessage.warning('申请内容已变化，请重新核验后再提交')
    return
  }
  submitting.value = true
  try {
    const result = await postData<LeaveRequest>('/leave', form)
    ElMessage.success(`申请 #${result.id} 已提交，等待${result.managerName}审批`)
    form.reason = ''
    form.medicalRecordId = null
    dateRange.value = null
    form.days = 1
    preview.value = null
    previewFingerprint.value = ''
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

.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.preview-card {
  margin: 2px 0 16px;
  padding: 14px;
  color: #344054;
  background: #f5fbf9;
  border: 1px solid #b9dfd5;
  border-radius: 6px;
}

.preview-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: #146b59;
}

.preview-heading span {
  color: #667085;
  font-size: 12px;
}

.preview-card dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.preview-card dl div { min-width: 0; }
.preview-card dt { color: #667085; font-size: 12px; }
.preview-card dd { margin: 4px 0 0; font-weight: 600; overflow-wrap: anywhere; }
.preview-card p { margin: 0; color: #667085; font-size: 12px; line-height: 1.6; }

@media (max-width: 560px) {
  .preview-card dl { grid-template-columns: 1fr; }
}
</style>
