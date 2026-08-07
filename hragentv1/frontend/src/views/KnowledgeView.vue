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
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh, Upload, View } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteData, getData, postData, putData, uploadKnowledgeFile } from '../api/http'
import type { KnowledgeArticle } from '../api/types'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const isHr = computed(() => auth.user?.role === 'HR')
const articles = ref<KnowledgeArticle[]>([])
const loading = ref(false)
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

async function load() {
  loading.value = true
  try {
    articles.value = await getData('/admin/knowledge')
  } finally {
    loading.value = false
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

onMounted(load)
</script>

<style scoped>
.title-actions {
  display: flex;
  gap: 10px;
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
</style>
