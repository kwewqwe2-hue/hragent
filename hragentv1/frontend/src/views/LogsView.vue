<template>
  <div>
    <div class="page-title">
      <div>
        <h1>调用与日志</h1>
        <p>查看全流程操作留痕，以及智能体调用记录。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <section class="content-panel">
      <el-tabs>
        <el-tab-pane label="操作日志">
          <el-table :data="logs" stripe>
            <el-table-column prop="createdAt" label="时间" width="180" />
            <el-table-column prop="actorName" label="操作人" width="120" />
            <el-table-column prop="action" label="动作" width="180" />
            <el-table-column prop="targetType" label="对象" width="130" />
            <el-table-column prop="targetId" label="对象ID" width="100" />
            <el-table-column prop="detail" label="详情" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="智能体调用">
          <el-table :data="calls" stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-box">
                  <p><strong>Prompt：</strong>{{ row.promptText }}</p>
                  <p><strong>Response：</strong>{{ row.responseText }}</p>
                  <p v-if="row.errorMessage"><strong>错误：</strong>{{ row.errorMessage }}</p>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" width="180" />
            <el-table-column prop="scenario" label="场景" width="160" />
            <el-table-column prop="provider" label="模型" width="180" />
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="responseText" label="响应摘要" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getData } from '../api/http'
import type { AiCallRecord, AuditLog } from '../api/types'

const logs = ref<AuditLog[]>([])
const calls = ref<AiCallRecord[]>([])

async function load() {
  logs.value = await getData('/admin/audit-logs')
  calls.value = await getData('/admin/ai-calls')
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
  white-space: pre-wrap;
}
</style>
