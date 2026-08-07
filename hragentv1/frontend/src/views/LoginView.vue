<template>
  <div class="login-page">
    <section class="login-panel">
      <div class="intro">
        <div class="brand-line">
          <span>HRAgent</span>
          <em>Demo</em>
        </div>
        <h1>员工请假管理 SaaS</h1>
        <p>员工申请、主管审批、HR 备案、知识库和智能体辅助判断。</p>
      </div>

      <el-form class="login-form" :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号">
          <el-input v-model="form.username" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" size="large" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
          登录
        </el-button>
        <div class="register-link">还没有账号？<router-link to="/register">注册账号</router-link></div>
      </el-form>

      <div class="accounts">
        <button @click="fill('zhangsan')">员工 zhangsan</button>
        <button @click="fill('lisi')">主管 lisi</button>
        <button @click="fill('wanghr')">HR wanghr</button>
        <button @click="fill('platformadmin')">平台管理员</button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({
  username: 'zhangsan',
  password: '123456'
})

function fill(username: string) {
  form.username = username
  form.password = '123456'
}

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push(auth.user?.platformAdmin ? '/platform-admin' : auth.hasActiveWorkspace ? '/dashboard' : '/workspace')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    linear-gradient(120deg, rgba(47, 128, 237, 0.12), rgba(16, 185, 129, 0.08)),
    #eef2f7;
}

.login-panel {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 28px;
  background: #fff;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  padding: 34px;
  box-shadow: 0 24px 70px rgba(23, 32, 51, 0.12);
}

.intro {
  padding: 18px 8px;
}

.brand-line {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #2f80ed;
  font-weight: 800;
}

.brand-line em {
  font-style: normal;
  color: #18794e;
  background: #e8f6ed;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
}

h1 {
  margin: 36px 0 12px;
  font-size: 34px;
  line-height: 1.2;
  color: #172033;
}

p {
  margin: 0;
  max-width: 420px;
  color: #687386;
  line-height: 1.8;
}

.login-form {
  padding: 8px 0;
}

.submit {
  width: 100%;
}

.register-link {
  margin-top: 14px;
  text-align: center;
  color: #687386;
  font-size: 14px;
}

.register-link a {
  color: #2f80ed;
  font-weight: 600;
}

.accounts {
  grid-column: 1 / -1;
  display: flex;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #e6ebf2;
}

.accounts button {
  border: 1px solid #d9e0ea;
  background: #f8fafc;
  color: #344054;
  border-radius: 6px;
  padding: 8px 12px;
  cursor: pointer;
}

@media (max-width: 720px) {
  .login-panel {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .accounts {
    flex-wrap: wrap;
  }
}
</style>
