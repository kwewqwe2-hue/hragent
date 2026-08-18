<template>
  <div>
    <div class="page-title">
      <div>
        <h1>智能体配置</h1>
        <p>配置当前公司的 DeepSeek 接口。密钥保存后只显示掩码。</p>
      </div>
      <el-tag :type="config?.enabled && config?.apiKeyConfigured ? 'success' : 'info'">
        {{ config?.enabled && config?.apiKeyConfigured ? '已启用' : '未启用' }}
      </el-tag>
    </div>

    <section class="content-panel config-panel" v-loading="loading">
      <el-form label-position="top" :model="form" @submit.prevent>
        <div class="form-grid">
          <el-form-item label="服务商">
            <el-input model-value="DeepSeek" disabled />
          </el-form-item>

          <el-form-item label="模型名称">
            <el-input v-model="form.model" placeholder="例如 deepseek-chat" />
          </el-form-item>

          <el-form-item class="full" label="API Base URL">
            <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com" />
          </el-form-item>

          <el-form-item class="full" label="API Key">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="keyPlaceholder"
            />
            <div class="field-note">留空会保留已保存的密钥，系统不会返回密钥明文。</div>
          </el-form-item>

          <el-form-item label="启用智能体">
            <el-switch v-model="form.enabled" />
          </el-form-item>
        </div>

        <div class="actions">
          <el-button type="primary" :icon="Check" :loading="saving" @click="save">保存配置</el-button>
          <el-button :icon="Connection" :loading="testing" @click="testConnection">测试连接</el-button>
        </div>
      </el-form>
    </section>

    <section class="content-panel status-panel">
      <div class="toolbar-row">
        <strong>配置状态</strong>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="密钥状态">
          {{ config?.apiKeyConfigured ? config.maskedApiKey : '未配置' }}
        </el-descriptions-item>
        <el-descriptions-item label="密钥来源">{{ sourceLabel }}</el-descriptions-item>
        <el-descriptions-item label="当前模型">{{ config?.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ config?.updatedAt || '尚未在管理端保存' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="testResult"
        class="test-result"
        :type="testResult.success ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="testResult.success ? `连接成功，耗时 ${testResult.latencyMs} ms` : '连接失败'"
        :description="testResult.message"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Connection, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getData, postData, putData } from '../api/http'
import type { AiConfig, AiConfigTestResult } from '../api/types'

const config = ref<AiConfig>()
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const testResult = ref<AiConfigTestResult>()

const form = reactive({
  baseUrl: 'https://api.deepseek.com',
  model: 'deepseek-v4-flash',
  apiKey: '',
  enabled: false
})

const keyPlaceholder = computed(() =>
  config.value?.apiKeyConfigured ? `已配置 ${config.value.maskedApiKey}` : '粘贴 DeepSeek API Key'
)

const sourceLabel = computed(() => {
  if (config.value?.credentialSource === 'DATABASE') return '管理端加密保存'
  if (config.value?.credentialSource === 'ENVIRONMENT') return 'Docker 环境变量'
  return '未配置'
})

async function load() {
  loading.value = true
  try {
    config.value = await getData<AiConfig>('/admin/ai-config')
    form.baseUrl = config.value.baseUrl
    form.model = config.value.model
    form.enabled = config.value.enabled
    form.apiKey = ''
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.baseUrl.trim() || !form.model.trim()) {
    ElMessage.warning('请填写 API Base URL 和模型名称')
    return
  }
  saving.value = true
  try {
    config.value = await putData<AiConfig>('/admin/ai-config', {
      baseUrl: form.baseUrl,
      model: form.model,
      apiKey: form.apiKey,
      enabled: form.enabled
    })
    form.apiKey = ''
    testResult.value = undefined
    ElMessage.success('智能体配置已保存')
  } finally {
    saving.value = false
  }
}

async function testConnection() {
  testing.value = true
  try {
    testResult.value = await postData<AiConfigTestResult>('/admin/ai-config/test')
    if (testResult.value.success) {
      ElMessage.success('DeepSeek 连接成功')
    }
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.config-panel {
  max-width: 920px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.full {
  grid-column: 1 / -1;
}

.field-note {
  margin-top: 6px;
  color: #687386;
  font-size: 12px;
}

.actions {
  display: flex;
  gap: 10px;
  padding-top: 4px;
}

.status-panel {
  max-width: 920px;
  margin-top: 16px;
}

.test-result {
  margin-top: 16px;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .full {
    grid-column: auto;
  }
}
</style>
