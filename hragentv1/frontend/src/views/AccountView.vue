<template>
  <div>
    <div class="page-title">
      <div>
        <h1>个人设置</h1>
        <p>账号ID：{{ auth.user?.publicId }}</p>
      </div>
    </div>

    <section class="content-panel settings-panel">
      <div class="profile-preview">
        <el-avatar :size="64" :src="profile.avatarUrl">{{ profile.name.slice(0, 1) }}</el-avatar>
        <div>
          <strong>{{ profile.name || auth.user?.username }}</strong>
          <span>{{ auth.user?.workspaceName || '尚未选择企业空间' }}</span>
        </div>
      </div>

      <el-form :model="profile" label-position="top">
        <el-form-item label="姓名">
          <el-input v-model="profile.name" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="profile.email" />
        </el-form-item>
        <el-form-item label="头像地址">
          <el-input v-model="profile.avatarUrl" placeholder="https://example.com/avatar.png" />
        </el-form-item>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存个人资料</el-button>
      </el-form>
    </section>

    <section class="content-panel dingtalk-panel">
      <div class="toolbar-row">
        <strong>钉钉账号绑定</strong>
        <el-tag :type="binding.bound ? 'success' : 'info'">
          {{ binding.bound ? '已绑定' : '未绑定' }}
        </el-tag>
      </div>
      <p class="binding-description">
        绑定后，HR 智能助理会使用钉钉身份查询你本人的员工档案和假期余额。绑定码十分钟内有效。
      </p>
      <div v-if="bindingCode" class="binding-code">
        <span>绑定码</span>
        <strong>{{ bindingCode }}</strong>
        <small>请在钉钉中向 HR 智能助理发送：绑定 {{ bindingCode }}</small>
      </div>
      <el-button type="primary" plain :loading="generatingCode" @click="generateBindingCode">
        {{ binding.bound ? '重新生成绑定码' : '生成绑定码' }}
      </el-button>
    </section>

    <section class="content-panel password-panel">
      <div class="toolbar-row"><strong>修改密码</strong></div>
      <el-form :model="password" label-position="top">
        <div class="password-grid">
          <el-form-item label="当前密码">
            <el-input v-model="password.current" type="password" show-password />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="password.next" type="password" show-password />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="password.confirm" type="password" show-password />
          </el-form-item>
        </div>
        <el-button :loading="changing" @click="savePassword">更新密码</el-button>
      </el-form>
    </section>

    <section class="content-panel danger-panel">
      <div class="toolbar-row"><strong>账号安全</strong></div>
      <p>永久注销会停用账号、解除企业关系并清除个人资料，此操作不可恢复。历史业务记录会保留用于审计。</p>
      <el-button type="danger" plain @click="deleteAccount">永久注销账号</el-button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watchEffect } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getData, postData } from '../api/http'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()
const saving = ref(false)
const changing = ref(false)
const generatingCode = ref(false)
const bindingCode = ref('')
const binding = reactive({ bound: false, maskedDingtalkUserId: '', boundAt: '' })
const profile = reactive({ name: '', email: '', avatarUrl: '' })
const password = reactive({ current: '', next: '', confirm: '' })

watchEffect(() => {
  profile.name = auth.user?.name || ''
  profile.email = auth.user?.email || ''
  profile.avatarUrl = auth.user?.avatarUrl || ''
})

onMounted(loadBindingStatus)

async function loadBindingStatus() {
  const data = await getData<typeof binding>('/auth/dingtalk-binding')
  Object.assign(binding, data)
}

async function generateBindingCode() {
  generatingCode.value = true
  try {
    const data = await postData<{ code: string }>('/auth/dingtalk-binding/code')
    bindingCode.value = data.code
    ElMessage.success('绑定码已生成，请在十分钟内发送给钉钉机器人')
  } finally {
    generatingCode.value = false
  }
}

async function saveProfile() {
  if (!profile.name.trim() || !profile.email.trim()) {
    ElMessage.warning('请填写姓名和邮箱')
    return
  }
  saving.value = true
  try {
    await auth.updateProfile(profile)
    ElMessage.success('个人资料已更新')
  } finally {
    saving.value = false
  }
}

async function savePassword() {
  if (password.next.length < 6) {
    ElMessage.warning('新密码至少需要6位')
    return
  }
  if (password.next !== password.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changing.value = true
  try {
    await auth.changePassword(password.current, password.next)
    password.current = ''
    password.next = ''
    password.confirm = ''
    ElMessage.success('密码已更新')
  } finally {
    changing.value = false
  }
}

async function deleteAccount() {
  await ElMessageBox.confirm(
    '永久注销后将无法登录，个人资料、企业关系和钉钉绑定会被清除，历史业务记录仅作为审计记录保留。确定继续吗？',
    '永久注销账号',
    { confirmButtonText: '继续注销', cancelButtonText: '取消', type: 'warning' }
  )
  const passwordResult = await ElMessageBox.prompt('请输入当前登录密码', '验证账号', {
    confirmButtonText: '下一步',
    cancelButtonText: '取消',
    inputType: 'password',
    inputPlaceholder: '当前密码',
    inputValidator: (value) => Boolean(value?.trim()) || '请输入当前密码'
  })
  const confirmationResult = await ElMessageBox.prompt('请输入“永久注销”以确认不可恢复操作', '最终确认', {
    confirmButtonText: '确认注销',
    cancelButtonText: '取消',
    inputPlaceholder: '永久注销',
    inputValidator: (value) => value === '永久注销' || '请输入：永久注销'
  })
  try {
    await postData('/auth/delete-account', {
      currentPassword: passwordResult.value,
      confirmation: confirmationResult.value
    })
    await ElMessageBox.alert('账号已永久注销，即将返回登录页。', '操作完成', {
      type: 'success',
      confirmButtonText: '返回登录'
    })
    localStorage.removeItem('hragent_token')
    localStorage.removeItem('hragent_user')
    localStorage.removeItem('hragent_workspaces')
    localStorage.removeItem('hragent_workspace_id')
    auth.$reset()
    await router.replace('/login')
  } catch {
    // The HTTP interceptor already displays the server error.
  }
}
</script>

<style scoped>
.settings-panel,
.dingtalk-panel,
.password-panel {
  max-width: 860px;
}

.password-panel {
  margin-top: 16px;
}

.danger-panel {
  max-width: 860px;
  margin-top: 16px;
  border-color: #f2c7c5;
}

.danger-panel p {
  color: #687386;
  font-size: 13px;
  line-height: 1.7;
}

.dingtalk-panel {
  margin-top: 16px;
}

.binding-description {
  color: #687386;
  font-size: 13px;
  line-height: 1.7;
}

.binding-code {
  display: grid;
  gap: 6px;
  margin: 16px 0;
  padding: 16px;
  border: 1px solid #d8dee8;
  border-radius: 6px;
  background: #f7f9fc;
}

.binding-code strong {
  font-size: 28px;
  letter-spacing: 0;
}

.binding-code small {
  color: #526070;
}

.profile-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 24px;
}

.profile-preview strong,
.profile-preview span {
  display: block;
}

.profile-preview span {
  margin-top: 4px;
  color: #687386;
  font-size: 13px;
}

.password-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 760px) {
  .password-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
