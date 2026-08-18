<template>
  <div>
    <div class="page-title">
      <div>
        <h1>平台管理</h1>
        <p>查看空间运行状态和脱敏调用元数据，不展示企业内部业务正文。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-card"><div class="label">企业空间</div><div class="value">{{ rows.length }}</div></div>
      <div class="stat-card"><div class="label">有效成员</div><div class="value">{{ totalMembers }}</div></div>
      <div class="stat-card"><div class="label">智能体调用</div><div class="value">{{ totalAiCalls }}</div></div>
      <div class="stat-card"><div class="label">开放 API 调用</div><div class="value">{{ totalApiCalls }}</div></div>
    </div>

    <section class="content-panel">
      <div class="toolbar-row"><strong>空间列表</strong><span class="muted">点击空间查看运行详情</span></div>
      <el-table :data="rows" stripe highlight-current-row @row-click="openDetail">
        <el-table-column prop="name" label="空间" min-width="180" />
        <el-table-column prop="code" label="空间码" width="150" />
        <el-table-column label="创建者" min-width="180">
          <template #default="{ row }">
            <div>{{ row.creatorName || '-' }}</div>
            <span class="muted">{{ row.creatorPublicId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="memberCount" label="有效成员" width="100" />
        <el-table-column prop="aiCallCount" label="智能体调用" width="120" />
        <el-table-column prop="apiCallCount" label="开放 API" width="110" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button :icon="View" size="small" aria-label="查看空间详情" @click.stop="openDetail(row)" />
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" :size="drawerSize" title="空间运行详情">
      <template v-if="detail">
        <div class="detail-head">
          <div class="workspace-mark">{{ detail.workspace.name.slice(0, 1).toUpperCase() }}</div>
          <div>
            <h2>{{ detail.workspace.name }}</h2>
            <p>{{ detail.workspace.code }} · 创建于 {{ detail.workspace.createdAt }}</p>
          </div>
          <el-tag :type="detail.workspace.active ? 'success' : 'info'">
            {{ detail.workspace.active ? '正常运行' : '已停用' }}
          </el-tag>
        </div>

        <div class="detail-stat-grid">
          <div><span>有效成员</span><strong>{{ detail.activeMemberCount }}</strong></div>
          <div><span>待加入/建档</span><strong>{{ detail.pendingMemberCount }}</strong></div>
          <div><span>已退出</span><strong>{{ detail.leftMemberCount }}</strong></div>
          <div><span>员工</span><strong>{{ detail.employeeCount }}</strong></div>
          <div><span>主管</span><strong>{{ detail.managerCount }}</strong></div>
          <div><span>空间管理员</span><strong>{{ detail.adminCount }}</strong></div>
        </div>

        <el-tabs v-model="detailTab" class="detail-tabs">
          <el-tab-pane label="空间概况" name="overview">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="空间 ID">{{ detail.workspace.workspaceId }}</el-descriptions-item>
              <el-descriptions-item label="空间码">{{ detail.workspace.code }}</el-descriptions-item>
              <el-descriptions-item label="创建者">{{ detail.workspace.creatorName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="创建者 ID">{{ detail.workspace.creatorPublicId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="开放 API 调用">{{ detail.workspace.apiCallCount }}</el-descriptions-item>
              <el-descriptions-item label="AI 调用">{{ detail.workspace.aiCallCount }}</el-descriptions-item>
            </el-descriptions>
          </el-tab-pane>

          <el-tab-pane :label="`开放 API 调用 (${detail.apiCalls.length})`" name="api">
            <el-table :data="detail.apiCalls" stripe empty-text="暂无 API 调用记录">
              <el-table-column prop="createdAt" label="时间" width="180" />
              <el-table-column prop="method" label="方法" width="80" />
              <el-table-column prop="path" label="接口路径" min-width="260" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.statusCode)">{{ row.statusCode }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`操作事件 (${detail.operations.length})`" name="operations">
            <el-table :data="detail.operations" stripe empty-text="暂无操作事件">
              <el-table-column prop="createdAt" label="时间" width="180" />
              <el-table-column prop="action" label="操作类型" min-width="220" />
              <el-table-column prop="targetType" label="目标类型" min-width="180" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane :label="`AI 调用 (${detail.aiCalls.length})`" name="ai">
            <el-table :data="detail.aiCalls" stripe empty-text="暂无 AI 调用记录">
              <el-table-column prop="createdAt" label="时间" width="180" />
              <el-table-column prop="scenario" label="调用场景" min-width="200" />
              <el-table-column prop="provider" label="供应商" width="130" />
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh, View } from '@element-plus/icons-vue'
import { getData } from '../api/http'
import type { PlatformWorkspaceDetail, PlatformWorkspaceOverview } from '../api/types'

const rows = ref<PlatformWorkspaceOverview[]>([])
const detail = ref<PlatformWorkspaceDetail | null>(null)
const detailVisible = ref(false)
const detailTab = ref('overview')
const drawerSize = window.innerWidth <= 820 ? '100%' : '880px'
const totalMembers = computed(() => rows.value.reduce((sum, row) => sum + row.memberCount, 0))
const totalAiCalls = computed(() => rows.value.reduce((sum, row) => sum + row.aiCallCount, 0))
const totalApiCalls = computed(() => rows.value.reduce((sum, row) => sum + row.apiCallCount, 0))

function statusType(statusCode: number) {
  if (statusCode >= 500) return 'danger'
  if (statusCode >= 400) return 'warning'
  return 'success'
}

async function load() {
  rows.value = await getData<PlatformWorkspaceOverview[]>('/platform-admin/workspaces')
}

async function openDetail(workspace: PlatformWorkspaceOverview) {
  detail.value = await getData<PlatformWorkspaceDetail>(`/platform-admin/workspaces/${workspace.workspaceId}`)
  detailTab.value = 'overview'
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.detail-head {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e4e7ec;
}

.workspace-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: #2f80ed;
  color: #fff;
  font-weight: 800;
}

.detail-head h2,
.detail-head p {
  margin: 0;
}

.detail-head h2 {
  font-size: 19px;
}

.detail-head p {
  margin-top: 5px;
  color: #687386;
  font-size: 13px;
}

.detail-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 18px 0;
  border-top: 1px solid #e4e7ec;
  border-left: 1px solid #e4e7ec;
}

.detail-stat-grid > div {
  min-height: 76px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 12px;
  border-right: 1px solid #e4e7ec;
  border-bottom: 1px solid #e4e7ec;
}

.detail-stat-grid span {
  color: #687386;
  font-size: 12px;
}

.detail-stat-grid strong {
  margin-top: 5px;
  font-size: 22px;
}

.detail-tabs {
  margin-top: 8px;
}

@media (max-width: 600px) {
  .detail-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-head {
    grid-template-columns: 48px minmax(0, 1fr);
  }

  .detail-head .el-tag {
    grid-column: 1 / -1;
    justify-self: start;
  }
}
</style>
