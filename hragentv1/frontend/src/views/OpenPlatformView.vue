<template>
  <div>
    <div class="page-title">
      <div>
        <h1>开放平台</h1>
        <p>当前身份：{{ roleLabel }} · {{ auth.user?.workspaceName }}</p>
      </div>
    </div>

    <section class="content-panel api-section">
      <div class="section-row">
        <div class="section-icon"><el-icon><Key /></el-icon></div>
        <div class="section-copy">
          <strong>个人平台 API</strong>
          <span>本人档案、假期余额、申请记录和同步状态</span>
        </div>
        <el-tag type="info">下一阶段</el-tag>
      </div>
    </section>

    <section class="content-panel api-section">
      <div class="section-row">
        <div class="section-icon agent"><el-icon><ChatDotRound /></el-icon></div>
        <div class="section-copy">
          <strong>个人智能体 API</strong>
          <span>根据当前空间身份授予智能体工具权限</span>
        </div>
        <el-tag type="info">下一阶段</el-tag>
      </div>
    </section>

    <section class="content-panel api-section">
      <div class="section-row">
        <div class="section-icon plugin"><el-icon><Grid /></el-icon></div>
        <div class="section-copy">
          <strong>插件市场</strong>
          <span>暂无可用插件</span>
        </div>
        <el-tag type="info">暂未开放</el-tag>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChatDotRound, Grid, Key } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const roleLabel = computed(() => {
  if (auth.user?.role === 'HR') return '空间管理员'
  if (auth.user?.role === 'MANAGER') return '主管'
  return '员工'
})
</script>

<style scoped>
.api-section {
  max-width: 900px;
  margin-bottom: 12px;
}

.section-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.section-icon {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  display: grid;
  place-items: center;
  color: #1d4ed8;
  background: #eaf2ff;
  border-radius: 8px;
  font-size: 20px;
}

.section-icon.agent {
  color: #047857;
  background: #e8f6ed;
}

.section-icon.plugin {
  color: #9a6700;
  background: #fff7e6;
}

.section-copy {
  min-width: 0;
  flex: 1;
}

.section-copy strong,
.section-copy span {
  display: block;
}

.section-copy span {
  margin-top: 4px;
  color: #687386;
  font-size: 13px;
}
</style>
