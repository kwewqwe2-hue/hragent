<template>
  <div class="register-page">
    <section class="register-panel">
      <div class="heading">
        <div class="brand">HRAgent</div>
        <h1>创建账号</h1>
        <p>注册后可以创建企业空间，或者使用空间码申请加入。</p>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <div class="field-grid">
          <el-form-item label="用户名">
            <el-input v-model="form.username" size="large" autocomplete="username" />
          </el-form-item>
          <el-form-item label="姓名">
            <el-input v-model="form.name" size="large" />
          </el-form-item>
        </div>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" size="large" autocomplete="email" />
        </el-form-item>
        <div class="field-grid">
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" size="large" show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPassword" type="password" size="large" show-password autocomplete="new-password" />
          </el-form-item>
        </div>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
          注册并继续
        </el-button>
        <div class="login-link">已有账号？<router-link to="/login">返回登录</router-link></div>
      </el-form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', name: '', email: '', password: '', confirmPassword: '' })

async function submit() {
  if (!form.username.trim() || !form.name.trim() || !form.email.trim()) {
    ElMessage.warning('请填写用户名、姓名和邮箱')
    return
  }
  if (form.password.length < 6) {
    ElMessage.warning('密码至少需要6位')
    return
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await auth.register({
      username: form.username,
      name: form.name,
      email: form.email,
      password: form.password
    })
    ElMessage.success('账号注册成功')
    router.push('/workspace')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #eef2f7;
}

.register-panel {
  width: min(720px, 100%);
  padding: 32px;
  background: #fff;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  box-shadow: 0 20px 60px rgba(23, 32, 51, 0.1);
}

.heading {
  margin-bottom: 24px;
}

.brand {
  color: #2f80ed;
  font-weight: 800;
}

h1 {
  margin: 20px 0 8px;
  font-size: 28px;
}

p {
  margin: 0;
  color: #687386;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.submit {
  width: 100%;
}

.login-link {
  margin-top: 16px;
  text-align: center;
  color: #687386;
}

.login-link a {
  color: #2f80ed;
  font-weight: 600;
}

@media (max-width: 640px) {
  .register-panel {
    padding: 24px;
  }

  .field-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
