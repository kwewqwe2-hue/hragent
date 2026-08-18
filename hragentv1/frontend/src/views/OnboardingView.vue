<template>
  <div>
    <div class="page-title">
      <div>
        <h1>{{ isHr ? '入职管理' : '入职办理' }}</h1>
        <p>{{ isHr ? '审核新员工提交的入职登记资料。' : '了解入职流程、提交登记资料并查看审核进度。' }}</p>
      </div>
      <el-tooltip content="刷新" placement="bottom">
        <el-button :icon="Refresh" circle :loading="loading" aria-label="刷新" @click="load" />
      </el-tooltip>
    </div>

    <template v-if="!isHr && !approvedRequest">
      <section class="process-band">
        <div v-for="(step, index) in steps" :key="step.title" class="process-step">
          <span>{{ index + 1 }}</span>
          <div><strong>{{ step.title }}</strong><small>{{ step.description }}</small></div>
        </div>
      </section>

      <el-row :gutter="16">
        <el-col :xs="24" :lg="14">
          <section class="content-panel">
            <div class="section-heading">
              <div><strong>入职登记表</strong><span>只填写后四位，Demo 不收集完整证件号和银行卡号。</span></div>
              <el-tag v-if="pendingRequest" type="warning">已有待审核申请</el-tag>
            </div>
            <el-form :model="form" label-position="top" :disabled="Boolean(pendingRequest)">
              <el-row :gutter="12">
                <el-col :xs="24" :sm="12"><el-form-item label="姓名" required><el-input v-model="form.legalName" maxlength="80" /></el-form-item></el-col>
                <el-col :xs="24" :sm="12"><el-form-item label="个人邮箱" required><el-input v-model="form.personalEmail" maxlength="120" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :xs="24" :sm="12"><el-form-item label="手机号" required><el-input v-model="form.phone" maxlength="40" /></el-form-item></el-col>
                <el-col :xs="24" :sm="12"><el-form-item label="证件号码后四位" required><el-input v-model="form.idNumberLast4" maxlength="4" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :xs="24" :sm="12"><el-form-item label="计划入职日期" required><el-date-picker v-model="form.plannedEntryDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
                <el-col :xs="24" :sm="12"><el-form-item label="工作地点" required><el-input v-model="form.workLocation" maxlength="120" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :xs="24" :sm="8"><el-form-item label="部门" required><el-input v-model="form.department" maxlength="80" /></el-form-item></el-col>
                <el-col :xs="24" :sm="8"><el-form-item label="岗位" required><el-input v-model="form.positionTitle" maxlength="80" /></el-form-item></el-col>
                <el-col :xs="24" :sm="8"><el-form-item label="直属主管"><el-input v-model="form.managerName" maxlength="80" /></el-form-item></el-col>
              </el-row>
              <div class="form-divider">紧急联系人与薪资账户</div>
              <el-row :gutter="12">
                <el-col :xs="24" :sm="12"><el-form-item label="紧急联系人" required><el-input v-model="form.emergencyContactName" maxlength="80" /></el-form-item></el-col>
                <el-col :xs="24" :sm="12"><el-form-item label="紧急联系电话" required><el-input v-model="form.emergencyContactPhone" maxlength="40" /></el-form-item></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :xs="24" :sm="8"><el-form-item label="开户行" required><el-input v-model="form.bankName" maxlength="120" /></el-form-item></el-col>
                <el-col :xs="24" :sm="8"><el-form-item label="银行卡后四位" required><el-input v-model="form.bankCardLast4" maxlength="4" /></el-form-item></el-col>
                <el-col :xs="24" :sm="8"><el-form-item label="最高学历" required><el-input v-model="form.highestEducation" maxlength="80" /></el-form-item></el-col>
              </el-row>
              <div class="form-divider">材料准备情况</div>
              <div class="check-grid">
                <el-checkbox v-model="form.idDocumentPrepared">身份证件复印件</el-checkbox>
                <el-checkbox v-model="form.bankCardPrepared">银行卡材料</el-checkbox>
                <el-checkbox v-model="form.educationCertificatePrepared">学历证明</el-checkbox>
                <el-checkbox v-model="form.photoPrepared">证件照</el-checkbox>
              </div>
              <el-form-item label="补充说明">
                <el-input v-model="form.remarks" type="textarea" :rows="3" maxlength="600" show-word-limit />
              </el-form-item>
              <el-button type="primary" :loading="submitting" :disabled="Boolean(pendingRequest)" @click="submit">
                提交 HR 审核
              </el-button>
            </el-form>
          </section>
        </el-col>

        <el-col :xs="24" :lg="10">
          <section class="content-panel records-panel">
            <div class="section-heading"><strong>办理进度</strong></div>
            <el-empty v-if="!requests.length && !loading" description="尚未提交入职登记" />
            <div v-for="request in requests" :key="request.id" class="status-record">
              <div class="record-head">
                <strong>申请 #{{ request.id }}</strong>
                <el-tag :type="statusType(request.status)">{{ request.statusLabel }}</el-tag>
              </div>
              <p>{{ request.department }} · {{ request.positionTitle }} · {{ request.plannedEntryDate }}</p>
              <small>提交于 {{ formatDateTime(request.submittedAt) }}</small>
              <el-alert v-if="request.hrOpinion" :title="`HR 意见：${request.hrOpinion}`" type="info" :closable="false" />
            </div>
          </section>
        </el-col>
      </el-row>
    </template>

    <section v-else-if="!isHr && approvedRequest" class="content-panel onboarding-welcome">
      <div class="welcome-mark">✓</div>
      <div>
        <el-tag type="success" effect="plain">入职审核已通过</el-tag>
        <h2>欢迎加入 {{ auth.user?.workspaceName || '企业空间' }}</h2>
        <p>你的员工档案已经建立，账号权限已切换为普通员工。现在可以进入员工工作台查看假期、发起请假和使用智能助手。</p>
        <el-alert title="报到指引：工牌和办公用品请到直属上级处领取；如暂不清楚直属上级，请联系 HR。" type="info" :closable="false" show-icon />
        <div class="onboarding-checklist">
          <div class="checklist-heading">
            <strong>入职办理进度</strong>
            <span>请由本人确认已完成的事项</span>
          </div>
          <div class="checklist-item completed">
            <span class="check-icon">✓</span>
            <div><strong>入职审核</strong><small>HR 已审核通过，员工档案已建立</small></div>
            <el-tag type="success" effect="plain">已完成</el-tag>
          </div>
          <div class="checklist-item" :class="{ completed: approvedRequest.officeSuppliesReceived }">
            <span class="check-icon">{{ approvedRequest.officeSuppliesReceived ? '✓' : '5' }}</span>
            <div><strong>办公用品领取</strong><small>到直属上级处领取工牌和办公用品</small></div>
            <el-tag v-if="approvedRequest.officeSuppliesReceived" type="success" effect="plain">已完成</el-tag>
            <el-button v-else type="primary" size="small" :loading="completingOfficeSupplies" @click="completeOfficeSupplies">
              我已完成
            </el-button>
          </div>
        </div>
        <div class="welcome-meta">
          <span>姓名：{{ approvedRequest.legalName }}</span>
          <span>部门：{{ approvedRequest.department }}</span>
          <span>岗位：{{ approvedRequest.positionTitle }}</span>
          <span>入职日期：{{ approvedRequest.plannedEntryDate }}</span>
        </div>
        <el-button type="primary" @click="router.push('/dashboard')">进入员工工作台</el-button>
      </div>
    </section>

    <section v-else class="content-panel">
      <div class="review-toolbar">
        <div><strong>企业入职申请</strong><el-tag type="warning">待处理 {{ pendingCount }}</el-tag></div>
        <el-select v-model="statusFilter" class="status-filter">
          <el-option label="全部状态" value="ALL" />
          <el-option label="待 HR 审核" value="PENDING_HR" />
          <el-option label="审核通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </div>
      <el-table :data="filteredRequests" stripe empty-text="暂无入职申请">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail-grid">
              <div><span>联系电话</span><strong>{{ row.phone }}</strong></div>
              <div><span>个人邮箱</span><strong>{{ row.personalEmail }}</strong></div>
              <div><span>证件后四位</span><strong>****{{ row.idNumberLast4 }}</strong></div>
              <div><span>工作地点</span><strong>{{ row.workLocation }}</strong></div>
              <div><span>直属主管</span><strong>{{ row.managerName || '待确认' }}</strong></div>
              <div><span>紧急联系人</span><strong>{{ row.emergencyContactName }} / {{ row.emergencyContactPhone }}</strong></div>
              <div><span>薪资账户</span><strong>{{ row.bankName }} / ****{{ row.bankCardLast4 }}</strong></div>
              <div><span>最高学历</span><strong>{{ row.highestEducation }}</strong></div>
              <div class="detail-wide"><span>材料</span><strong>{{ materialSummary(row) }}</strong></div>
              <div v-if="row.remarks" class="detail-wide"><span>补充说明</span><strong>{{ row.remarks }}</strong></div>
              <div v-if="row.hrOpinion" class="detail-wide"><span>HR 意见</span><strong>{{ row.hrOpinion }}</strong></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="新员工" min-width="140"><template #default="{ row }"><strong>{{ row.legalName }}</strong><small class="cell-secondary">{{ row.employeeNo }}</small></template></el-table-column>
        <el-table-column label="部门 / 岗位" min-width="190"><template #default="{ row }">{{ row.department }} / {{ row.positionTitle }}</template></el-table-column>
        <el-table-column prop="plannedEntryDate" label="计划入职" width="120" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.statusLabel }}</el-tag></template></el-table-column>
        <el-table-column label="提交时间" width="150"><template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_HR'">
              <el-button type="success" size="small" @click="review(row, true)">通过</el-button>
              <el-button type="danger" size="small" @click="review(row, false)">驳回</el-button>
            </template>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getData, postData, putData } from '../api/http'
import type { OnboardingRequest, OnboardingRequestStatus } from '../api/types'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()
const isHr = computed(() => auth.user?.role === 'HR')
const isNewHire = computed(() => auth.user?.role === 'NEW_HIRE')
const requests = ref<OnboardingRequest[]>([])
const loading = ref(false)
const submitting = ref(false)
const completingOfficeSupplies = ref(false)
const statusFilter = ref<'ALL' | OnboardingRequestStatus>('ALL')
const steps = [
  { title: '了解安排', description: '确认岗位、地点和报到日期' },
  { title: '填写登记', description: '在线提交个人与材料信息' },
  { title: 'HR 审核', description: 'HR 核对资料并反馈结果' },
  { title: '现场报到', description: '按通知携带原件完成核验' },
  { title: '办公用品领取', description: '到直属上级处领取工牌和办公用品' }
]
const form = reactive({
  legalName: '陈晨', phone: '13800000004', personalEmail: 'chenchen@example.com', idNumberLast4: '',
  plannedEntryDate: '2026-09-01', department: '研发中心', positionTitle: '测试工程师', managerName: '李四',
  workLocation: '北京市海淀区', emergencyContactName: '', emergencyContactPhone: '', bankName: '',
  bankCardLast4: '', highestEducation: '本科', idDocumentPrepared: false, bankCardPrepared: false,
  educationCertificatePrepared: false, photoPrepared: false, remarks: ''
})

const pendingRequest = computed(() => requests.value.find((item) => item.status === 'PENDING_HR'))
const approvedRequest = computed(() => requests.value.find((item) => item.status === 'APPROVED'))
const pendingCount = computed(() => requests.value.filter((item) => item.status === 'PENDING_HR').length)
const filteredRequests = computed(() => statusFilter.value === 'ALL'
  ? requests.value : requests.value.filter((item) => item.status === statusFilter.value))

async function load() {
  loading.value = true
  try {
    const loaded = await getData<OnboardingRequest[]>(isHr.value ? '/onboarding/hr/all' : '/onboarding/my')
    requests.value = loaded
    if (!isHr.value && loaded.some((item) => item.status === 'APPROVED') && isNewHire.value) {
      await auth.refreshMe()
      await auth.refreshWorkspaces()
    }
  } finally { loading.value = false }
}

function validate() {
  const required = [form.legalName, form.phone, form.personalEmail, form.idNumberLast4, form.plannedEntryDate,
    form.department, form.positionTitle, form.workLocation, form.emergencyContactName,
    form.emergencyContactPhone, form.bankName, form.bankCardLast4, form.highestEducation]
  if (required.some((value) => !String(value).trim())) return '请填写所有必填字段'
  if (!/^[0-9A-Za-z]{4}$/.test(form.idNumberLast4)) return '证件号码后四位必须是 4 位数字或字母'
  if (!/^\d{4}$/.test(form.bankCardLast4)) return '银行卡后四位必须是 4 位数字'
  return ''
}

async function submit() {
  const message = validate()
  if (message) { ElMessage.warning(message); return }
  await ElMessageBox.confirm('提交后将进入 HR 审核，审核前不可重复提交。', '确认提交入职登记', {
    confirmButtonText: '确认提交', cancelButtonText: '继续填写', type: 'warning'
  })
  submitting.value = true
  try {
    await postData('/onboarding', form)
    ElMessage.success('入职登记已提交，等待 HR 审核')
    await load()
  } finally { submitting.value = false }
}

async function completeOfficeSupplies() {
  if (!approvedRequest.value || approvedRequest.value.officeSuppliesReceived) return
  completingOfficeSupplies.value = true
  try {
    await putData('/onboarding/my/office-supplies')
    ElMessage.success('办公用品领取进度已完成')
    await load()
  } finally {
    completingOfficeSupplies.value = false
  }
}

async function review(request: OnboardingRequest, approved: boolean) {
  const { value } = await ElMessageBox.prompt(
    approved ? '可填写审核意见' : '请填写驳回原因',
    approved ? '审核通过' : '驳回申请',
    { confirmButtonText: approved ? '确认通过' : '确认驳回', cancelButtonText: '取消',
      inputPlaceholder: approved ? '例如：资料齐全，请按时报到' : '驳回原因',
      inputValidator: (value) => approved || Boolean(value?.trim()) || '驳回时必须填写原因' }
  )
  await putData(`/onboarding/hr/${request.id}/review`, { approved, opinion: value?.trim() || '' })
  ElMessage.success(approved ? '入职申请已通过' : '入职申请已驳回')
  await load()
}

function statusType(status: OnboardingRequestStatus) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function materialSummary(request: OnboardingRequest) {
  return [request.idDocumentPrepared && '身份证件', request.bankCardPrepared && '银行卡材料',
    request.educationCertificatePrepared && '学历证明', request.photoPrepared && '证件照']
    .filter(Boolean).join('、') || '尚未准备'
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

let onboardingTimer: number | undefined

onMounted(() => {
  void load()
  onboardingTimer = window.setInterval(() => {
    if (isNewHire.value) void load()
  }, 15000)
})

onBeforeUnmount(() => {
  if (onboardingTimer !== undefined) window.clearInterval(onboardingTimer)
})
</script>

<style scoped>
.process-band { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:1px; margin-bottom:16px; border:1px solid #d9e0ea; background:#d9e0ea; }
.process-step { min-height:76px; display:flex; align-items:center; gap:12px; padding:14px; background:#fff; }
.process-step > span { width:28px; height:28px; display:grid; place-items:center; flex:0 0 auto; border-radius:50%; color:#fff; background:#28786e; font-weight:700; }
.process-step strong,.process-step small { display:block; }
.process-step small { margin-top:4px; color:#687386; }
.section-heading,.review-toolbar,.record-head { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.section-heading { margin-bottom:16px; }
.section-heading span { display:block; margin-top:3px; color:#687386; font-size:12px; }
.form-divider { margin:6px 0 14px; padding-top:14px; border-top:1px solid #e5e9f0; color:#344054; font-weight:650; }
.check-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:6px 16px; margin-bottom:16px; }
.records-panel { min-height:240px; }
.onboarding-welcome { display:flex; align-items:flex-start; gap:22px; max-width:860px; padding:34px; }
.welcome-mark { width:56px; height:56px; flex:0 0 auto; display:grid; place-items:center; border-radius:50%; color:#fff; background:#28786e; font-size:30px; font-weight:700; }
.onboarding-welcome h2 { margin:12px 0 8px; color:#1d2939; }
.onboarding-welcome p { max-width:680px; margin:0 0 18px; color:#667085; line-height:1.7; }
.onboarding-checklist { max-width:680px; margin:18px 0 22px; border:1px solid #dfe5ec; border-radius:6px; overflow:hidden; }
.checklist-heading { display:flex; justify-content:space-between; gap:12px; padding:14px 16px; background:#f7f9fc; border-bottom:1px solid #e8edf3; }
.checklist-heading span { color:#687386; font-size:12px; }
.checklist-item { display:flex; align-items:center; gap:12px; padding:14px 16px; border-bottom:1px solid #e8edf3; }
.checklist-item:last-child { border-bottom:0; }
.checklist-item > div { flex:1; min-width:0; }
.checklist-item strong,.checklist-item small { display:block; }
.checklist-item small { margin-top:4px; color:#687386; }
.check-icon { width:26px; height:26px; display:grid; place-items:center; flex:0 0 auto; border:1px solid #cdd5df; border-radius:50%; color:#687386; font-weight:700; }
.checklist-item.completed .check-icon { color:#fff; border-color:#28786e; background:#28786e; }
.welcome-meta { display:flex; flex-wrap:wrap; gap:8px 20px; margin-bottom:22px; color:#475467; font-size:13px; }
.status-record { padding:14px 0; border-bottom:1px solid #e5e9f0; }
.status-record:last-child { border-bottom:0; }
.status-record p { margin:8px 0 4px; color:#475467; }
.status-record small { color:#687386; }
.status-record .el-alert { margin-top:10px; }
.review-toolbar { margin-bottom:14px; }
.review-toolbar > div { display:flex; align-items:center; gap:10px; }
.status-filter { width:180px; }
.cell-secondary { display:block; margin-top:3px; color:#687386; font-size:12px; }
.detail-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px 24px; padding:12px 44px; }
.detail-grid span,.detail-grid strong { display:block; }
.detail-grid span { color:#687386; font-size:12px; }
.detail-grid strong { margin-top:4px; color:#344054; }
.detail-wide { grid-column:1/-1; }
@media (max-width:1100px) { .process-band { grid-template-columns:repeat(3,minmax(0,1fr)); } }
@media (max-width:900px) { .process-band { grid-template-columns:repeat(2,minmax(0,1fr)); } }
@media (max-width:560px) { .process-band,.check-grid,.detail-grid { grid-template-columns:1fr; } }
</style>
