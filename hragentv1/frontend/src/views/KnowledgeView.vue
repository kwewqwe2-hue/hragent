<template>
  <div class="knowledge-page">
    <div class="page-title">
      <div>
        <h1>知识库</h1>
        <p>公司制度文档由 n8n RAG 统一索引，SaaS 保存文档元数据。</p>
      </div>
      <div class="title-actions" v-if="isHr">
        <el-button :icon="Upload" @click="openUpload">导入文档</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增文章</el-button>
      </div>
    </div>

    <section v-if="isHr" class="policy-demo-bar">
      <div class="policy-demo-info">
        <div class="policy-demo-title">
          <strong>政策网站监测演示源</strong>
          <el-tag type="danger" effect="plain" size="small">非真实政府网站</el-tag>
          <el-tag v-if="demoPolicy" :type="demoPolicy.updateAvailable ? 'warning' : 'success'" size="small">
            {{ demoPolicy.version }}
          </el-tag>
        </div>
        <span>{{ demoPolicy?.title || '正在读取演示政策源...' }}</span>
      </div>
      <div class="policy-demo-actions">
        <el-button :icon="Link" @click="openPolicySource">查看网站</el-button>
        <el-button
          type="primary"
          :loading="policyBusy"
          :disabled="!demoPolicy?.updateAvailable"
          @click="publishDemoPolicy"
        >
          发布 V2
        </el-button>
        <el-button
          :icon="Refresh"
          :loading="policyBusy"
          :disabled="demoPolicy?.version === '2026.1'"
          @click="resetDemoPolicy"
        >
          重置 V1
        </el-button>
      </div>
    </section>

    <section v-if="isHr" class="policy-monitor-results">
      <div class="policy-monitor-heading">
        <div>
          <div class="policy-monitor-title">
            <strong>政策更新候选</strong>
            <el-tag v-if="pendingPolicyCount" type="warning" size="small">
              {{ pendingPolicyCount }} 条待审核
            </el-tag>
          </div>
          <span>n8n 发现网站内容变化后，在这里生成候选记录；当前步骤不会自动写入知识库。</span>
        </div>
        <el-button size="small" :icon="Refresh" :loading="candidateLoading" @click="loadPolicyCandidates">
          刷新记录
        </el-button>
      </div>
      <el-alert
        v-if="policyError"
        class="policy-monitor-error"
        :title="policyError"
        type="error"
        show-icon
        :closable="false"
      />
      <el-table v-if="policyCandidates.length" :data="policyCandidates" size="small" stripe>
        <el-table-column prop="version" label="版本" width="90">
          <template #default="{ row }"><el-tag size="small">{{ row.version }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="title" label="政策标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="changeSummary" label="变更摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="发现时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.detectedAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="policyStatusType(row.reviewStatus)" size="small">
              {{ policyStatusLabel(row.reviewStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Link" @click="openCandidateSource(row)">查看原文</el-button>
            <el-button
              v-if="row.reviewStatus === 'PENDING_REVIEW'"
              link
              type="warning"
              :icon="DocumentChecked"
              @click="openPolicyReview(row)"
            >
              审核
            </el-button>
            <el-button
              v-else-if="row.knowledgeArticleId"
              link
              type="success"
              :icon="View"
              @click="showPolicyArticle(row)"
            >
              查看入库
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!candidateLoading" :image-size="56" description="尚未发现政策更新" />
    </section>

    <el-dialog
      v-model="reviewVisible"
      title="审核政策更新候选"
      width="720px"
      destroy-on-close
      @closed="resetPolicyReview"
    >
      <template v-if="reviewCandidate">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="政策标题" :span="2">{{ reviewCandidate.title }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ reviewCandidate.version }}</el-descriptions-item>
          <el-descriptions-item label="适用地区">{{ reviewCandidate.region || '未标注' }}</el-descriptions-item>
          <el-descriptions-item label="发布日期">{{ reviewCandidate.publishedAt || '未标注' }}</el-descriptions-item>
          <el-descriptions-item label="生效日期">{{ reviewCandidate.effectiveAt || '未标注' }}</el-descriptions-item>
          <el-descriptions-item label="来源" :span="2">{{ reviewCandidate.sourceName }}</el-descriptions-item>
        </el-descriptions>

        <div class="policy-review-block">
          <strong>变更摘要</strong>
          <p>{{ reviewCandidate.changeSummary || reviewCandidate.summary || '来源未提供变更摘要' }}</p>
        </div>
        <div class="policy-review-block policy-review-content">
          <strong>政策正文</strong>
          <p>{{ reviewCandidate.content }}</p>
        </div>

        <el-form :model="reviewForm" label-position="top" class="policy-review-form">
          <el-form-item label="审核结果" required>
            <el-radio-group v-model="reviewForm.decision">
              <el-radio-button value="APPROVED">通过并写入知识库</el-radio-button>
              <el-radio-button value="REJECTED">驳回</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="reviewForm.decision === 'REJECTED' ? '驳回原因' : '审核意见'">
            <el-input
              v-model="reviewForm.opinion"
              type="textarea"
              :rows="3"
              maxlength="600"
              show-word-limit
              :placeholder="reviewForm.decision === 'REJECTED' ? '驳回时必须填写原因' : '可填写审核备注'"
            />
          </el-form-item>
          <el-alert
            v-if="reviewForm.decision === 'APPROVED'"
            title="通过后会立即同步到 n8n RAG，并在下方知识库生成正式文章。"
            type="info"
            :closable="false"
          />
        </el-form>
      </template>
      <template #footer>
        <el-button :disabled="reviewing" @click="reviewVisible = false">取消</el-button>
        <el-button
          :type="reviewForm.decision === 'APPROVED' ? 'primary' : 'danger'"
          :loading="reviewing"
          @click="submitPolicyReview"
        >
          确认{{ reviewForm.decision === 'APPROVED' ? '通过' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <section class="content-panel">
      <el-table :data="articles" stripe v-loading="loading">
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="title" label="文档名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="region" label="适用范围" width="120" />
        <el-table-column prop="source" label="来源" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="130" />
        <el-table-column prop="reviewStatus" label="状态" width="120" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :icon="View" @click="show(row)">查看</el-button>
            <el-button v-if="isHr" size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="isHr" size="small" :icon="Refresh" @click="openReindex(row)">重新索引</el-button>
            <el-button v-if="isHr" size="small" type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && articles.length === 0" description="暂无知识库内容" />
    </section>

    <el-dialog v-model="uploadVisible" :title="reindexArticleId ? '重新索引知识文档' : '导入制度文档到 n8n RAG'" width="560px" @closed="resetUpload">
      <el-form :model="uploadForm" label-position="top">
        <el-form-item label="文档文件" required>
          <input
            ref="fileInput"
            class="file-picker"
            type="file"
            accept=".pdf,.txt,.csv,.xlsx"
            @change="onFileChange"
          >
          <div class="file-name">{{ selectedFile?.name || '请选择 PDF、TXT、CSV 或 XLSX 文件' }}</div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类" required>
              <el-input v-model="uploadForm.category" placeholder="例如：假期制度" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用范围">
              <el-input v-model="uploadForm.region" placeholder="例如：全国" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="来源">
          <el-input v-model="uploadForm.source" placeholder="例如：HR 制度中心" />
        </el-form-item>
        <el-alert
          v-if="reindexArticleId"
          title="请尽量使用与原文相同的文件名，n8n 会按文件名替换旧向量。"
          type="info"
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="upload">{{ reindexArticleId ? '重新上传并索引' : '上传并索引' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="readVisible" :title="current?.title" width="760px">
      <div class="article-meta">
        {{ current?.category }} · {{ current?.region }} · {{ current?.source }} · {{ current?.reviewStatus }}
      </div>
      <p class="article-body">{{ current?.content }}</p>
    </el-dialog>

    <el-dialog v-model="editVisible" :title="editingId ? '编辑知识库文章' : '新增知识库文章'" width="760px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分类" required>
              <el-input v-model="form.category" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="正文" required>
          <el-input v-model="form.content" type="textarea" :rows="8" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="来源">
              <el-input v-model="form.source" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用范围">
              <el-input v-model="form.region" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="发布日期">
              <el-date-picker v-model="form.publishedAt" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="更新时间">
              <el-date-picker v-model="form.updatedAt" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="审核状态">
              <el-input v-model="form.reviewStatus" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { Delete, DocumentChecked, Edit, Link, Plus, Refresh, Upload, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteData, getData, postData, putData, uploadKnowledgeFile } from '../api/http'
import type { DemoPolicy, KnowledgeArticle, PolicyMonitorCandidate, PolicyReviewStatus } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isHr = computed(() => auth.user?.role === 'HR')
const articles = ref<KnowledgeArticle[]>([])
const demoPolicy = ref<DemoPolicy | null>(null)
const policyCandidates = ref<PolicyMonitorCandidate[]>([])
const loading = ref(false)
const policyBusy = ref(false)
const candidateLoading = ref(false)
const policyError = ref('')
const reviewVisible = ref(false)
const reviewing = ref(false)
const reviewCandidate = ref<PolicyMonitorCandidate | null>(null)
const readVisible = ref(false)
const editVisible = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const current = ref<KnowledgeArticle | null>(null)
const editingId = ref<number | null>(null)
const reindexArticleId = ref<number | null>(null)
const selectedFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const form = reactive({
  category: '',
  title: '',
  content: '',
  source: '',
  region: '',
  publishedAt: '',
  updatedAt: '',
  reviewStatus: ''
})

const uploadForm = reactive({
  category: '公司制度',
  source: 'SaaS 知识库上传',
  region: '全国'
})

const reviewForm = reactive<{
  decision: 'APPROVED' | 'REJECTED'
  opinion: string
}>({
  decision: 'APPROVED',
  opinion: ''
})

let policyRefreshTimer: number | undefined

const pendingPolicyCount = computed(() =>
  policyCandidates.value.filter((item) => item.reviewStatus === 'PENDING_REVIEW').length
)

async function load() {
  loading.value = true
  try {
    articles.value = await getData('/admin/knowledge')
  } finally {
    loading.value = false
  }
}

async function loadDemoPolicy() {
  demoPolicy.value = await getData<DemoPolicy>('/demo-policy/current')
}

async function loadPolicyCandidates() {
  candidateLoading.value = true
  policyError.value = ''
  try {
    policyCandidates.value = await getData<PolicyMonitorCandidate[]>('/admin/policy-monitor/candidates')
  } catch (error) {
    policyError.value = error instanceof Error ? error.message : '政策候选加载失败，请检查登录状态和后端服务'
  } finally {
    candidateLoading.value = false
  }
}

async function refreshPolicyMonitor() {
  if (!isHr.value) return
  await Promise.all([loadDemoPolicy(), loadPolicyCandidates()])
}

function policyStatusLabel(status: PolicyReviewStatus) {
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已驳回'
  return '待审核'
}

function policyStatusType(status: PolicyReviewStatus) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'warning'
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(new Date(value))
}

function openCandidateSource(candidate: PolicyMonitorCandidate) {
  const popup = window.open(candidate.sourceUrl, '_blank', 'noopener,noreferrer')
  if (popup) popup.opener = null
}

function openPolicyReview(candidate: PolicyMonitorCandidate) {
  reviewCandidate.value = candidate
  reviewForm.decision = 'APPROVED'
  reviewForm.opinion = ''
  reviewVisible.value = true
}

function resetPolicyReview() {
  reviewCandidate.value = null
  reviewForm.decision = 'APPROVED'
  reviewForm.opinion = ''
}

async function submitPolicyReview() {
  if (!reviewCandidate.value) return
  if (reviewForm.decision === 'REJECTED' && !reviewForm.opinion.trim()) {
    ElMessage.warning('驳回时必须填写原因')
    return
  }
  reviewing.value = true
  try {
    await postData(`/admin/policy-monitor/candidates/${reviewCandidate.value.id}/review`, {
      decision: reviewForm.decision,
      opinion: reviewForm.opinion.trim() || undefined
    })
    ElMessage.success(reviewForm.decision === 'APPROVED' ? '政策已通过审核并写入知识库' : '政策候选已驳回')
    reviewVisible.value = false
    await Promise.all([loadPolicyCandidates(), load()])
  } finally {
    reviewing.value = false
  }
}

function showPolicyArticle(candidate: PolicyMonitorCandidate) {
  const article = articles.value.find((item) => item.id === candidate.knowledgeArticleId)
  if (!article) {
    ElMessage.info('知识库文章正在刷新，请稍后再试')
    void load()
    return
  }
  show(article)
}

function openPolicySource() {
  const popup = window.open('/policy-source-demo', '_blank', 'noopener,noreferrer')
  if (popup) popup.opener = null
}

async function publishDemoPolicy() {
  await ElMessageBox.confirm(
    '发布后，固定演示网址会从 2026.1 更新为 2026.2，供后续 n8n 监测。',
    '发布演示政策新版',
    {
      confirmButtonText: '发布 V2',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  policyBusy.value = true
  try {
    demoPolicy.value = await postData<DemoPolicy>('/demo-policy/publish-next')
    await loadPolicyCandidates()
    ElMessage.success('政策演示站已更新为 2026.2')
  } finally {
    policyBusy.value = false
  }
}

async function resetDemoPolicy() {
  await ElMessageBox.confirm(
    '重置后可重新演示“发现网站更新”的完整过程。',
    '重置演示政策',
    {
      confirmButtonText: '重置 V1',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  policyBusy.value = true
  try {
    demoPolicy.value = await postData<DemoPolicy>('/demo-policy/reset')
    await loadPolicyCandidates()
    ElMessage.success('政策演示站已重置为 2026.1')
  } finally {
    policyBusy.value = false
  }
}

function show(row: KnowledgeArticle) {
  current.value = row
  readVisible.value = true
}

function openUpload() {
  reindexArticleId.value = null
  uploadForm.category = '公司制度'
  uploadForm.source = 'SaaS 知识库上传'
  uploadForm.region = '全国'
  selectedFile.value = null
  uploadVisible.value = true
}

function openReindex(row: KnowledgeArticle) {
  reindexArticleId.value = row.id
  uploadForm.category = row.category
  uploadForm.source = row.source || 'SaaS 知识库上传'
  uploadForm.region = row.region || '全国'
  selectedFile.value = null
  uploadVisible.value = true
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0] ?? null
  if (!file) {
    selectedFile.value = null
    return
  }
  if (file.size === 0) {
    selectedFile.value = null
    input.value = ''
    ElMessage.warning('文件内容为空，请重新选择文件')
    return
  }
  selectedFile.value = file
}

function resetUpload() {
  reindexArticleId.value = null
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

async function upload() {
  if (!selectedFile.value) {
    ElMessage.warning('请选择一个非空的知识库文件')
    return
  }
  const extension = selectedFile.value.name.split('.').pop()?.toLowerCase() || ''
  if (!['pdf', 'txt', 'csv', 'xlsx'].includes(extension)) {
    ElMessage.warning('仅支持 PDF、TXT、CSV、XLSX 文件')
    return
  }
  if (!uploadForm.category.trim()) {
    ElMessage.warning('请填写分类')
    return
  }
  uploading.value = true
  try {
    await uploadKnowledgeFile(selectedFile.value, { ...uploadForm, articleId: reindexArticleId.value })
    ElMessage.success(reindexArticleId.value ? '文件已重新索引' : '文件已导入并完成 RAG 索引')
    uploadVisible.value = false
    await load()
  } finally {
    uploading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    category: '公司制度',
    title: '',
    content: '',
    source: 'HR 手动维护',
    region: '全国',
    publishedAt: '',
    updatedAt: '',
    reviewStatus: 'APPROVED'
  })
  editVisible.value = true
}

function openEdit(row: KnowledgeArticle) {
  editingId.value = row.id
  Object.assign(form, row)
  editVisible.value = true
}

async function save() {
  if (editingId.value) {
    await putData(`/admin/knowledge/${editingId.value}`, form)
  } else {
    await postData('/admin/knowledge', form)
  }
  ElMessage.success('保存成功')
  editVisible.value = false
  await load()
}

async function remove(row: KnowledgeArticle) {
  await ElMessageBox.confirm(`确认删除《${row.title}》？`, '删除知识库文章', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await deleteData(`/admin/knowledge/${row.id}`)
  ElMessage.success('删除成功')
  await load()
}

onMounted(() => {
  void load()
  if (isHr.value) {
    void refreshPolicyMonitor()
    policyRefreshTimer = window.setInterval(() => {
      void refreshPolicyMonitor()
    }, 30_000)
  }
})

onBeforeUnmount(() => {
  if (policyRefreshTimer !== undefined) {
    window.clearInterval(policyRefreshTimer)
  }
})
</script>

<style scoped>
.title-actions {
  display: flex;
  gap: 10px;
}

.policy-demo-bar {
  margin-bottom: 16px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #f7f9f8;
  border: 1px solid #d8e1de;
  border-left: 3px solid #4d8178;
}

.policy-demo-info {
  min-width: 0;
}

.policy-demo-title,
.policy-demo-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.policy-demo-title strong {
  color: #26332f;
  font-size: 14px;
}

.policy-demo-info > span {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: #687572;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.policy-demo-actions {
  flex: 0 0 auto;
}

.policy-monitor-results {
  margin-bottom: 16px;
  padding: 16px 0;
  border-top: 1px solid #d8e1de;
  border-bottom: 1px solid #d8e1de;
}

.policy-monitor-heading {
  margin-bottom: 12px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.policy-monitor-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.policy-monitor-heading strong {
  color: #26332f;
  font-size: 15px;
}

.policy-monitor-heading span {
  display: block;
  margin-top: 5px;
  color: #687572;
  font-size: 13px;
}

.policy-review-block {
  padding: 14px 0;
  border-bottom: 1px solid #e4ebe8;
}

.policy-review-block strong {
  color: #26332f;
  font-size: 14px;
}

.policy-review-block p {
  margin: 8px 0 0;
  color: #55635f;
  line-height: 1.7;
  white-space: pre-wrap;
}

.policy-review-content {
  max-height: 220px;
  overflow: auto;
}

.policy-review-form {
  padding-top: 16px;
}

.file-picker {
  width: 100%;
  padding: 10px;
  border: 1px dashed #c9d2df;
  border-radius: 6px;
  background: #f8fafc;
}

.file-name {
  margin-top: 8px;
  color: #667085;
  font-size: 13px;
}

.article-meta {
  color: #687386;
  margin-bottom: 12px;
}

.article-body {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #344054;
}

@media (max-width: 900px) {
  .policy-demo-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .policy-demo-actions {
    width: 100%;
  }

  .policy-monitor-heading {
    flex-direction: column;
  }
}
</style>
