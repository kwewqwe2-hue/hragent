<template>
  <div>
    <div class="page-title">
      <div>
        <h1>{{ isHr ? '在职证明管理' : '在职证明' }}</h1>
        <p>提交证明申请并查看审核及文件生成状态。</p>
      </div>
      <el-tooltip content="刷新" placement="bottom">
        <el-button :icon="Refresh" circle :loading="loading" aria-label="刷新" @click="load" />
      </el-tooltip>
    </div>

    <el-tabs v-if="isHr" v-model="activeTab" class="certificate-tabs">
      <el-tab-pane label="我的申请" name="mine" />
      <el-tab-pane label="HR 审批" name="review" />
      <el-tab-pane label="签证模板" name="templates" />
    </el-tabs>

    <template v-if="activeTab === 'mine'">
      <el-row :gutter="16">
        <el-col :xs="24" :lg="9">
          <section class="content-panel request-panel">
            <div class="section-heading">发起申请</div>
            <el-form :model="form" label-position="top">
              <el-form-item label="证明类型" required>
                <el-select v-model="form.certificateType" style="width: 100%">
                  <el-option
                    v-for="item in options.certificateTypes"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="文件语言" required>
                <el-select v-model="form.language" style="width: 100%">
                  <el-option
                    v-for="item in options.languages"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="用途" required>
                <el-input v-model="form.purpose" maxlength="200" placeholder="例如：办理银行业务" />
              </el-form-item>
              <template v-if="form.certificateType === 'VISA'">
                <el-form-item label="目的国家或地区" required>
                  <el-input v-model="form.destinationCountry" maxlength="100" />
                </el-form-item>
                <el-form-item label="领事馆或受理机构" required>
                  <el-input v-model="form.consulateName" maxlength="160" />
                </el-form-item>
                <el-form-item label="专用 DOCX 模板（可选）">
                  <input
                    ref="requestTemplateFileInput"
                    class="file-picker"
                    type="file"
                    accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    @change="onRequestTemplateChange"
                  >
                  <div v-if="!requestTemplateFile" class="file-name">仅支持 DOCX，最大 5 MB</div>
                  <el-alert
                    v-if="requestTemplatePreviewLoading"
                    title="正在检查 Word 模板"
                    type="info"
                    :closable="false"
                    show-icon
                  />
                  <div v-else-if="requestTemplatePreview" class="template-preview request-template-preview">
                    <el-alert
                      :title="requestTemplatePreview.canUpload ? '模板检查通过' : '模板检查未通过'"
                      :type="requestTemplatePreview.canUpload ? 'success' : 'error'"
                      :closable="false"
                      show-icon
                    />
                    <div v-if="requestTemplatePreview.placeholders.length" class="preview-tags">
                      <el-tag v-for="item in requestTemplatePreview.placeholders" :key="item" size="small">{{ item }}</el-tag>
                    </div>
                    <div v-if="requestTemplatePreview.unsupportedPlaceholders.length" class="preview-tags">
                      <el-tag
                        v-for="item in requestTemplatePreview.unsupportedPlaceholders"
                        :key="item"
                        type="danger"
                        size="small"
                      >{{ item }}</el-tag>
                    </div>
                    <ul v-if="requestTemplatePreview.warnings.length" class="preview-warnings">
                      <li v-for="warning in requestTemplatePreview.warnings" :key="warning">{{ warning }}</li>
                    </ul>
                  </div>
                </el-form-item>
                <el-form-item v-if="requestTemplateFile" label="模板名称" required>
                  <el-input v-model="requestTemplateName" maxlength="120" />
                </el-form-item>
              </template>
              <el-form-item label="证明中显示薪资">
                <el-switch v-model="form.includeSalary" />
              </el-form-item>
              <el-form-item label="补充说明">
                <el-input v-model="form.remarks" type="textarea" :rows="3" maxlength="600" show-word-limit />
              </el-form-item>
              <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
            </el-form>
          </section>
        </el-col>

        <el-col :xs="24" :lg="15">
          <section class="content-panel records-panel">
            <div class="section-heading">申请记录</div>
            <el-empty v-if="!myRequests.length && !loading" description="暂无证明申请" />
            <el-table v-else :data="myRequests" stripe>
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div class="detail-grid">
                    <div><span>申请用途</span><strong>{{ row.purpose }}</strong></div>
                    <div><span>语言</span><strong>{{ row.languageLabel }}</strong></div>
                    <div><span>提交时间</span><strong>{{ formatDateTime(row.submittedAt) }}</strong></div>
                    <div v-if="row.destinationCountry"><span>目的地</span><strong>{{ row.destinationCountry }}</strong></div>
                    <div v-if="row.consulateName"><span>受理机构</span><strong>{{ row.consulateName }}</strong></div>
                    <div><span>显示薪资</span><strong>{{ row.includeSalary ? '是' : '否' }}</strong></div>
                    <div v-if="row.remarks"><span>补充说明</span><strong>{{ row.remarks }}</strong></div>
                    <div v-if="row.hrOpinion"><span>HR 意见</span><strong>{{ row.hrOpinion }}</strong></div>
                    <div v-if="row.requestedTemplateFileName"><span>提交模板</span><strong>{{ row.requestedTemplateFileName }}</strong></div>
                    <div v-if="row.sourceTemplateFileName"><span>使用模板</span><strong>{{ row.sourceTemplateFileName }}</strong></div>
                    <div v-if="row.generationError"><span>生成说明</span><strong>{{ row.generationError }}</strong></div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="certificateTypeLabel" label="证明类型" min-width="145" />
              <el-table-column prop="purpose" label="用途" min-width="140" show-overflow-tooltip />
              <el-table-column label="状态" width="145">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ row }">
                  <el-tooltip v-if="row.documentReady" content="下载 Word" placement="top">
                    <el-button :icon="Download" circle aria-label="下载 Word" @click="downloadDocument(row)" />
                  </el-tooltip>
                  <el-button v-else-if="row.canCancel" type="danger" link @click="cancel(row.id)">取消</el-button>
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
            </el-table>
          </section>
        </el-col>
      </el-row>
    </template>

    <section v-else-if="activeTab === 'review'" class="content-panel review-panel">
      <div class="review-toolbar">
        <div class="review-summary">
          <strong>企业申请</strong>
          <el-tag type="warning">待处理 {{ pendingCount }}</el-tag>
        </div>
        <el-select v-model="statusFilter" class="status-filter" aria-label="状态筛选">
          <el-option label="全部状态" value="ALL" />
          <el-option label="待 HR 审核" value="PENDING_HR" />
          <el-option label="审核通过，待生成" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="证明已生成" value="GENERATED" />
          <el-option label="生成失败" value="GENERATION_FAILED" />
        </el-select>
      </div>

      <el-empty v-if="!filteredHrRequests.length && !loading" description="当前没有匹配的申请" />
      <el-table v-else :data="filteredHrRequests" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail-grid">
              <div><span>员工</span><strong>{{ row.employeeNo }} · {{ row.employeeName }}</strong></div>
              <div><span>部门与岗位</span><strong>{{ row.department || '-' }} · {{ row.title || '-' }}</strong></div>
              <div><span>申请用途</span><strong>{{ row.purpose }}</strong></div>
              <div><span>文件语言</span><strong>{{ row.languageLabel }}</strong></div>
              <div v-if="row.destinationCountry"><span>目的地</span><strong>{{ row.destinationCountry }}</strong></div>
              <div v-if="row.consulateName"><span>受理机构</span><strong>{{ row.consulateName }}</strong></div>
              <div><span>显示薪资</span><strong>{{ row.includeSalary ? '是' : '否' }}</strong></div>
              <div v-if="row.remarks"><span>补充说明</span><strong>{{ row.remarks }}</strong></div>
              <div v-if="row.hrOpinion"><span>HR 意见</span><strong>{{ row.hrOpinion }}</strong></div>
              <div v-if="row.requestedTemplateId" class="detail-template">
                <span>员工提交模板</span>
                <strong>{{ row.requestedTemplateFileName }}</strong>
                <el-button type="primary" link :icon="Download" @click="downloadRequestedTemplate(row)">下载核验</el-button>
              </div>
              <div v-if="row.sourceTemplateFileName"><span>使用模板</span><strong>{{ row.sourceTemplateFileName }}</strong></div>
              <div v-if="row.generationError"><span>生成说明</span><strong>{{ row.generationError }}</strong></div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="员工" width="120">
          <template #default="{ row }">
            <strong>{{ row.employeeName }}</strong>
            <div class="cell-secondary">{{ row.employeeNo }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="certificateTypeLabel" label="证明类型" width="145" />
        <el-table-column prop="purpose" label="用途" min-width="140" show-overflow-tooltip />
        <el-table-column label="员工模板" width="105">
          <template #default="{ row }">
            <el-tag v-if="row.requestedTemplateId" :type="requestTemplateTagType(row.status)">
              {{ requestTemplateStatusText(row.status) }}
            </el-tag>
            <span v-else class="muted">企业模板</span>
          </template>
        </el-table-column>
        <el-table-column label="档案" width="100">
          <template #default="{ row }">
            <el-tooltip v-if="!row.profileReady" :content="`缺少：${row.missingProfileFields.join('、')}`" placement="top">
              <el-tag type="danger">信息不完整</el-tag>
            </el-tooltip>
            <el-tag v-else type="success">可生成</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="135">
          <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING_HR'">
              <el-button type="success" size="small" :icon="Check" @click="review(row, true)">通过</el-button>
              <el-button type="danger" size="small" :icon="Close" @click="review(row, false)">驳回</el-button>
            </template>
            <el-tooltip v-else-if="row.documentReady" content="下载 Word" placement="left">
              <el-button :icon="Download" circle aria-label="下载 Word" @click="downloadDocument(row)" />
            </el-tooltip>
            <el-button
              v-else-if="row.certificateType === 'VISA' && ['APPROVED', 'GENERATION_FAILED'].includes(row.status)"
              type="primary"
              size="small"
              :icon="Refresh"
              @click="retryGeneration(row)"
            >重新生成</el-button>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-else class="content-panel template-panel">
      <div class="review-toolbar">
        <div>
          <div class="section-heading">签证/领事馆模板</div>
          <div class="panel-hint">HR 审核后，系统按目的国家、受理机构和语言匹配最新启用模板。</div>
        </div>
        <el-button type="primary" :icon="Upload" @click="openTemplateUpload">上传 Word 模板</el-button>
      </div>

      <el-table v-if="templates.length || loading" v-loading="loading" :data="templates" stripe>
        <el-table-column prop="name" label="模板名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="适用对象" min-width="230">
          <template #default="{ row }">
            <strong>{{ row.destinationCountry }}</strong>
            <div class="cell-secondary">{{ row.consulateName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="languageLabel" label="语言" width="100" />
        <el-table-column prop="sourceFileName" label="文件" min-width="180" show-overflow-tooltip />
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="templateReviewType(row.reviewStatus)">{{ row.reviewStatusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="145">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="下载模板" placement="top">
              <el-button :icon="Download" circle aria-label="下载模板" @click="downloadTemplate(row)" />
            </el-tooltip>
            <el-switch
              v-if="row.reviewStatus === 'APPROVED'"
              :model-value="row.active"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="toggleTemplate(row, Boolean($event))"
            />
            <span v-else class="muted">随申请处理</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无签证模板，请先上传公司或领事馆提供的 Word 模板" />
    </section>

    <el-dialog
      v-model="templateUploadVisible"
      class="template-upload-dialog"
      body-class="template-upload-dialog__body"
      title="上传签证/领事馆 Word 模板"
      width="min(560px, calc(100vw - 32px))"
      align-center
      @closed="resetTemplateUpload"
    >
      <el-form :model="templateForm" label-position="top">
        <el-form-item label="Word 模板文件" required>
          <input
            ref="templateFileInput"
            class="file-picker"
            type="file"
            accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            @change="onTemplateFileChange"
          >
          <div v-if="!selectedTemplateFile" class="file-name">请选择 DOCX 格式的 Word 文件，最大 5 MB</div>
          <el-alert
            v-if="templatePreviewLoading"
            title="正在检查 Word 模板"
            type="info"
            :closable="false"
            show-icon
          />
          <div v-else-if="templatePreview" class="template-preview">
            <el-alert
              :title="templatePreview.canUpload ? '模板检查通过' : '模板检查未通过'"
              :type="templatePreview.canUpload ? 'success' : 'error'"
              :closable="false"
              show-icon
            />
            <div v-if="templatePreview.placeholders.length" class="preview-line">
              <span>已识别字段</span>
              <div class="preview-tags">
                <el-tag v-for="item in templatePreview.placeholders" :key="item" size="small">{{ item }}</el-tag>
              </div>
            </div>
            <div v-if="templatePreview.unsupportedPlaceholders.length" class="preview-line preview-error">
              <span>不支持字段</span>
              <div class="preview-tags">
                <el-tag v-for="item in templatePreview.unsupportedPlaceholders" :key="item" type="danger" size="small">{{ item }}</el-tag>
              </div>
            </div>
            <ul v-if="templatePreview.warnings.length" class="preview-warnings">
              <li v-for="warning in templatePreview.warnings" :key="warning">{{ warning }}</li>
            </ul>
          </div>
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.name" maxlength="120" placeholder="例如：德国商务签证中英双语模板" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="目的国家或地区" required>
              <el-input v-model="templateForm.destinationCountry" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模板语言" required>
              <el-select v-model="templateForm.language" style="width: 100%">
                <el-option
                  v-for="item in options.languages"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="领事馆或受理机构" required>
          <el-input v-model="templateForm.consulateName" maxlength="160" placeholder="例如：德国驻华大使馆" />
        </el-form-item>
        <el-alert
          title="模板使用 {{fieldName}} 占位符"
          description="支持 legalName、englishName、employeeNo、department、title、entryDate、passportNumber、passportExpiryDate、monthlySalary、currency、companyName、issueDate、purpose、destinationCountry、consulateName。"
          type="info"
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button @click="templateUploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="templateUploading" @click="uploadTemplate">上传模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { Check, Close, Download, Refresh, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createCertificateWithTemplate,
  downloadBinary,
  getData,
  postData,
  previewCertificateTemplate,
  putData,
  uploadCertificateTemplate
} from '../api/http'
import type {
  CertificateLanguage,
  CertificateRequestStatus,
  EmploymentCertificateOptions,
  EmploymentCertificateRequest,
  EmploymentCertificateTemplate,
  EmploymentCertificateTemplatePreview,
  EmploymentCertificateType
} from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isHr = computed(() => auth.user?.role === 'HR')
const activeTab = ref(isHr.value ? 'review' : 'mine')
const loading = ref(false)
const submitting = ref(false)
const statusFilter = ref<'ALL' | CertificateRequestStatus>('ALL')
const options = ref<EmploymentCertificateOptions>({ certificateTypes: [], languages: [] })
const myRequests = ref<EmploymentCertificateRequest[]>([])
const hrRequests = ref<EmploymentCertificateRequest[]>([])
const templates = ref<EmploymentCertificateTemplate[]>([])
const templateUploadVisible = ref(false)
const templateUploading = ref(false)
const selectedTemplateFile = ref<File | null>(null)
const templateFileInput = ref<HTMLInputElement | null>(null)
const templatePreviewLoading = ref(false)
const templatePreview = ref<EmploymentCertificateTemplatePreview | null>(null)
const requestTemplateFile = ref<File | null>(null)
const requestTemplateFileInput = ref<HTMLInputElement | null>(null)
const requestTemplateName = ref('')
const requestTemplatePreviewLoading = ref(false)
const requestTemplatePreview = ref<EmploymentCertificateTemplatePreview | null>(null)

const form = reactive({
  certificateType: 'STANDARD' as EmploymentCertificateType,
  language: 'CHINESE' as CertificateLanguage,
  purpose: '',
  destinationCountry: '',
  consulateName: '',
  includeSalary: false,
  remarks: ''
})

const templateForm = reactive({
  name: '',
  destinationCountry: '',
  consulateName: '',
  language: 'BILINGUAL' as CertificateLanguage
})

const filteredHrRequests = computed(() => statusFilter.value === 'ALL'
  ? hrRequests.value
  : hrRequests.value.filter((request) => request.status === statusFilter.value))
const pendingCount = computed(() => hrRequests.value.filter((request) => request.status === 'PENDING_HR').length)

async function load() {
  loading.value = true
  try {
    const [loadedOptions, loadedMine] = await Promise.all([
      getData<EmploymentCertificateOptions>('/employment-certificates/options'),
      getData<EmploymentCertificateRequest[]>('/employment-certificates/my')
    ])
    options.value = loadedOptions
    myRequests.value = loadedMine
    if (isHr.value) {
      const [loadedHrRequests, loadedTemplates] = await Promise.all([
        getData<EmploymentCertificateRequest[]>('/employment-certificates/hr/all'),
        getData<EmploymentCertificateTemplate[]>('/employment-certificate-templates')
      ])
      hrRequests.value = loadedHrRequests
      templates.value = loadedTemplates
    }
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!form.purpose.trim()) {
    ElMessage.warning('请填写证明用途')
    return
  }
  if (form.certificateType === 'VISA' && (!form.destinationCountry.trim() || !form.consulateName.trim())) {
    ElMessage.warning('请填写目的国家或地区以及领事馆或受理机构')
    return
  }
  submitting.value = true
  try {
    if (requestTemplateFile.value) {
      if (requestTemplatePreviewLoading.value) {
        ElMessage.info('正在检查模板，请稍候')
        return
      }
      if (!requestTemplatePreview.value?.canUpload) {
        ElMessage.warning('员工模板检查未通过，请修正后重新选择')
        return
      }
      if (!requestTemplateName.value.trim()) {
        ElMessage.warning('请填写模板名称')
        return
      }
      await createCertificateWithTemplate(requestTemplateFile.value, {
        templateName: requestTemplateName.value,
        language: form.language,
        purpose: form.purpose,
        destinationCountry: form.destinationCountry,
        consulateName: form.consulateName,
        includeSalary: form.includeSalary,
        remarks: form.remarks
      })
      ElMessage.success('申请和模板已提交，等待 HR 一次审核')
    } else {
      await postData('/employment-certificates', form)
      ElMessage.success('申请已提交，等待 HR 审核')
    }
    resetForm()
    await load()
  } finally {
    submitting.value = false
  }
}

function onRequestTemplateChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  requestTemplateFile.value = null
  requestTemplatePreview.value = null
  requestTemplateName.value = ''
  if (!file) return
  if (file.size === 0 || file.size > 5 * 1024 * 1024 || !file.name.toLowerCase().endsWith('.docx')) {
    input.value = ''
    ElMessage.warning(file.size === 0 ? '文件内容为空' : '请选择不超过 5 MB 的 DOCX 文件')
    return
  }
  requestTemplateFile.value = file
  requestTemplateName.value = file.name.replace(/\.docx$/i, '')
  void previewRequestTemplate(file)
}

async function previewRequestTemplate(file: File) {
  requestTemplatePreviewLoading.value = true
  try {
    requestTemplatePreview.value = await previewCertificateTemplate(file)
  } catch {
    requestTemplatePreview.value = null
  } finally {
    requestTemplatePreviewLoading.value = false
  }
}

async function cancel(id: number) {
  await ElMessageBox.confirm('确定取消这条在职证明申请吗？', '取消申请', {
    confirmButtonText: '确定取消',
    cancelButtonText: '返回',
    type: 'warning'
  })
  await putData(`/employment-certificates/${id}/cancel`)
  ElMessage.success('申请已取消')
  await load()
}

async function review(row: EmploymentCertificateRequest, approved: boolean) {
  if (approved && !row.profileReady) {
    ElMessage.warning(`员工档案缺少：${row.missingProfileFields.join('、')}`)
    return
  }
  const { value } = await ElMessageBox.prompt(
    approved
      ? (row.requestedTemplateId ? '通过后将同时批准员工模板并生成证明' : '确认通过该证明申请')
      : '请填写驳回原因',
    approved ? '审核通过' : '驳回申请',
    {
      inputValue: approved ? '员工信息核验通过，同意开具。' : '',
      inputPlaceholder: approved ? '可填写审核意见' : '请说明需要补充或更正的内容',
      inputValidator: (text: string) => approved || text.trim() ? true : '驳回时必须填写原因',
      confirmButtonText: approved ? '确认通过' : '确认驳回',
      cancelButtonText: '取消'
    }
  )
  const updated = await putData<EmploymentCertificateRequest>(
    `/employment-certificates/hr/${row.id}/review`,
    { approved, opinion: value }
  )
  if (!approved) {
    ElMessage.success('申请已驳回')
  } else if (updated.status === 'GENERATED') {
    ElMessage.success('审核通过，Word 证明已生成')
  } else if (updated.status === 'GENERATION_FAILED') {
    ElMessage.error(`审核通过，但生成失败：${updated.generationError || '未知错误'}`)
  } else {
    ElMessage.success('审核通过，等待匹配专用模板')
  }
  await load()
}

async function downloadDocument(row: EmploymentCertificateRequest) {
  await downloadBinary(
    `/employment-certificates/${row.id}/download`,
    row.generatedFileName || `在职证明-${row.employeeNo}.docx`
  )
  ElMessage.success('Word 文件已下载')
}

async function downloadRequestedTemplate(row: EmploymentCertificateRequest) {
  if (!row.requestedTemplateId) return
  await downloadBinary(
    `/employment-certificate-templates/${row.requestedTemplateId}/download`,
    row.requestedTemplateFileName || 'employee-template.docx'
  )
  ElMessage.success('员工模板已下载')
}

async function retryGeneration(row: EmploymentCertificateRequest) {
  const updated = await postData<EmploymentCertificateRequest>(
    `/employment-certificates/hr/${row.id}/generate`,
    {}
  )
  if (updated.status === 'GENERATED') {
    ElMessage.success('Word 证明已重新生成')
  } else if (updated.status === 'GENERATION_FAILED') {
    ElMessage.error(`生成失败：${updated.generationError || '未知错误'}`)
  } else {
    ElMessage.warning(updated.generationError || '仍未找到完全匹配的启用模板')
  }
  await load()
}

function openTemplateUpload() {
  templateForm.name = ''
  templateForm.destinationCountry = ''
  templateForm.consulateName = ''
  templateForm.language = 'BILINGUAL'
  selectedTemplateFile.value = null
  templatePreview.value = null
  templateUploadVisible.value = true
}

function onTemplateFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) {
    selectedTemplateFile.value = null
    templatePreview.value = null
    return
  }
  if (file.size === 0) {
    selectedTemplateFile.value = null
    templatePreview.value = null
    input.value = ''
    ElMessage.warning('文件内容为空，请重新选择')
    return
  }
  selectedTemplateFile.value = file
  templatePreview.value = null
  void previewTemplate(file)
}

async function previewTemplate(file: File) {
  templatePreviewLoading.value = true
  try {
    templatePreview.value = await previewCertificateTemplate(file)
  } catch {
    templatePreview.value = null
  } finally {
    templatePreviewLoading.value = false
  }
}

function resetTemplateUpload() {
  selectedTemplateFile.value = null
  templatePreview.value = null
  templatePreviewLoading.value = false
  if (templateFileInput.value) templateFileInput.value.value = ''
}

async function uploadTemplate() {
  if (!selectedTemplateFile.value) {
    ElMessage.warning('请选择一个 DOCX 模板文件')
    return
  }
  const extension = selectedTemplateFile.value.name.split('.').pop()?.toLowerCase() || ''
  if (extension !== 'docx') {
    ElMessage.warning('仅支持 DOCX 格式的 Word 模板')
    return
  }
  if (selectedTemplateFile.value.size > 5 * 1024 * 1024) {
    ElMessage.warning('Word 模板不能超过 5 MB')
    return
  }
  if (templatePreviewLoading.value) {
    ElMessage.info('正在检查模板，请稍候')
    return
  }
  if (!templatePreview.value) {
    ElMessage.warning('请先选择有效的 DOCX 模板并等待检查结果')
    return
  }
  if (!templatePreview.value.canUpload) {
    ElMessage.warning('模板检查未通过，请修正不支持的占位符后重新上传')
    return
  }
  if (!templateForm.name.trim() || !templateForm.destinationCountry.trim() || !templateForm.consulateName.trim()) {
    ElMessage.warning('请填写模板名称、目的国家或地区以及受理机构')
    return
  }
  templateUploading.value = true
  try {
    await uploadCertificateTemplate(selectedTemplateFile.value, templateForm)
    ElMessage.success('模板已上传并启用')
    templateUploadVisible.value = false
    await load()
  } finally {
    templateUploading.value = false
  }
}

async function toggleTemplate(row: EmploymentCertificateTemplate, active: boolean) {
  try {
    await putData(`/employment-certificate-templates/${row.id}/active`, { active })
    ElMessage.success(active ? '模板已启用' : '模板已停用')
    await load()
  } catch {
    await load()
  }
}

async function downloadTemplate(row: EmploymentCertificateTemplate) {
  await downloadBinary(`/employment-certificate-templates/${row.id}/download`, row.sourceFileName)
  ElMessage.success('模板已下载')
}

function resetForm() {
  form.certificateType = 'STANDARD'
  form.language = 'CHINESE'
  form.purpose = ''
  form.destinationCountry = ''
  form.consulateName = ''
  form.includeSalary = false
  form.remarks = ''
  requestTemplateFile.value = null
  requestTemplateName.value = ''
  requestTemplatePreview.value = null
  requestTemplatePreviewLoading.value = false
  if (requestTemplateFileInput.value) requestTemplateFileInput.value.value = ''
}

function statusType(status: CertificateRequestStatus) {
  if (status === 'GENERATED') return 'success'
  if (status === 'APPROVED') return 'primary'
  if (status === 'REJECTED' || status === 'GENERATION_FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  return 'warning'
}

function templateReviewType(status: EmploymentCertificateTemplate['reviewStatus']) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  return 'warning'
}

function requestTemplateTagType(status: CertificateRequestStatus) {
  if (status === 'GENERATED' || status === 'APPROVED') return 'success'
  if (status === 'REJECTED' || status === 'GENERATION_FAILED') return 'danger'
  if (status === 'CANCELLED') return 'info'
  return 'warning'
}

function requestTemplateStatusText(status: CertificateRequestStatus) {
  if (status === 'GENERATED' || status === 'APPROVED') return '已入库'
  if (status === 'REJECTED') return '已驳回'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'GENERATION_FAILED') return '已入库，生成失败'
  return '待随申请审核'
}

function formatDateTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-'
}

function formatFileSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

onMounted(load)
</script>

<style scoped>
.certificate-tabs {
  margin-bottom: 14px;
}

:global(.template-upload-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 32px);
  margin: 0 auto;
  overflow: hidden;
}

:global(.template-upload-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.template-preview {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.preview-line {
  display: grid;
  gap: 6px;
  padding: 8px 10px;
  background: #f7f9fc;
  border: 1px solid #e5eaf2;
  border-radius: 6px;
}

.preview-line > span {
  color: #606a7a;
  font-size: 12px;
}

.preview-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-error {
  background: #fff6f6;
  border-color: #f5c2c7;
}

.preview-warnings {
  margin: 0;
  padding: 8px 10px 8px 28px;
  color: #8a5a00;
  background: #fff9e8;
  border: 1px solid #f5df9b;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.request-panel,
.records-panel,
.review-panel,
.template-panel {
  min-height: 340px;
}

.panel-hint {
  color: #687386;
  font-size: 12px;
  line-height: 1.6;
}

.section-heading {
  margin-bottom: 16px;
  font-size: 15px;
  font-weight: 700;
}

.review-toolbar,
.review-summary {
  display: flex;
  align-items: center;
}

.review-toolbar {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.review-summary {
  gap: 12px;
}

.status-filter {
  width: 210px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
  padding: 12px 36px 18px;
}

.detail-grid span,
.detail-grid strong {
  display: block;
}

.detail-grid span,
.cell-secondary {
  color: #687386;
  font-size: 12px;
}

.detail-grid strong {
  margin-top: 4px;
  color: #25324b;
  font-weight: 500;
  line-height: 1.55;
}

.cell-secondary {
  margin-top: 3px;
}

@media (max-width: 900px) {
  .records-panel {
    margin-top: 16px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
