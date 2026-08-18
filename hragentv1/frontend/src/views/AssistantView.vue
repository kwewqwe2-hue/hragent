<template>
  <div class="launch-state">
    <el-icon class="is-loading" :size="24"><Loading /></el-icon>
    <span>正在打开 HRAgent AI</span>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'

onMounted(() => {
  window.location.assign('http://localhost:5174')
})
</script>

<style scoped>
.launch-state {
  height: calc(100vh - 104px);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #687386;
  background: #ffffff;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
}
</style>
