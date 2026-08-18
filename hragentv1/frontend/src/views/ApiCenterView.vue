<template>
  <div>
    <div class="page-title">
      <div>
        <h1>接口中心</h1>
        <p>管理外部系统接入凭证，查看真实 OpenAPI 和调用日志。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="createKey">创建 API Key</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <section class="content-panel">
          <div class="toolbar-row">
            <strong>API Key</strong>
            <el-button :icon="Refresh" @click="load">刷新</el-button>
          </div>
          <el-table :data="keys" stripe>
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="keyPrefix" label="前缀" width="140" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastUsedAt" label="最近使用" min-width="160" />
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button size="small" @click="toggle(row)">{{ row.active ? '停用' : '启用' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-col>

      <el-col :xs="24" :lg="12">
        <section class="content-panel">
          <div class="toolbar-row">
            <strong>接口列表</strong>
          </div>
          <el-table :data="endpoints" stripe>
            <el-table-column prop="method" label="方法" width="90" />
            <el-table-column prop="path" label="路径" />
            <el-table-column prop="desc" label="说明" />
          </el-table>
          <el-alert class="tip" type="info" :closable="false" show-icon>
            <template #title>调用时在请求头中加入 X-API-Key: 你的密钥</template>
          </el-alert>
        </section>
      </el-col>
    </el-row>

    <section class="content-panel logs">
      <div class="toolbar-row">
        <strong>接口调用日志</strong>
      </div>
      <el-table :data="logs" stripe>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column prop="method" label="方法" width="90" />
        <el-table-column prop="path" label="路径" min-width="240" />
        <el-table-column prop="statusCode" label="状态码" width="90" />
        <el-table-column prop="message" label="说明" />
      </el-table>
    </section>

    <el-dialog v-model="keyVisible" title="API Key 已创建" width="640px">
      <el-alert type="warning" :closable="false" show-icon>
        <template #title>请立即保存这个密钥。关闭窗口后系统不会再次显示明文。</template>
      </el-alert>
      <el-input class="api-key" :model-value="createdKey" readonly>
        <template #append>
          <el-button @click="copyKey">复制</el-button>
        </template>
      </el-input>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getData, http, postData } from '../api/http'
import type { ApiCallLog, ApiKeyCreateResponse, ApiKeyView } from '../api/types'

const keys = ref<ApiKeyView[]>([])
const logs = ref<ApiCallLog[]>([])
const keyVisible = ref(false)
const createdKey = ref('')
const endpoints = [
  { method: 'GET', path: '/api/openapi/v1/employees/{employeeNo}', desc: '按工号查询员工' },
  { method: 'GET', path: '/api/openapi/v1/balances/{employeeNo}', desc: '按工号查询假期余额' },
  { method: 'POST', path: '/api/openapi/v1/employees/sync', desc: '同步或更新员工' }
]

async function load() {
  keys.value = await getData('/admin/api-keys')
  logs.value = await getData('/admin/api-call-logs')
}

async function createKey() {
  const { value } = await ElMessageBox.prompt('请输入 API Key 名称', '创建 API Key', {
    inputValue: 'Default integration key',
    confirmButtonText: '创建',
    cancelButtonText: '取消'
  })
  const response = await postData<ApiKeyCreateResponse>('/admin/api-keys', { name: value })
  createdKey.value = response.apiKey
  keyVisible.value = true
  await load()
}

async function toggle(row: ApiKeyView) {
  await http.patch(`/admin/api-keys/${row.id}/active?active=${!row.active}`)
  ElMessage.success('状态已更新')
  await load()
}

async function copyKey() {
  await navigator.clipboard.writeText(createdKey.value)
  ElMessage.success('已复制')
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-top: 14px;
}

.logs {
  margin-top: 16px;
}

.api-key {
  margin-top: 14px;
}
</style>
