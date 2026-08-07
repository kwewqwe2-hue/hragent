<template>
  <main v-if="!session" class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-brand">
        <span class="brand-mark"><Sparkles :size="22" /></span>
        <div>
          <strong>HRAgent AI</strong>
          <span>企业智能助手</span>
        </div>
      </div>

      <div class="login-heading">
        <h1 id="login-title">登录</h1>
        <p>使用 HRAgent SaaS 账号</p>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <label>
          <span>账号</span>
          <input v-model.trim="loginForm.username" autocomplete="username" autofocus />
        </label>
        <label>
          <span>密码</span>
          <input v-model="loginForm.password" type="password" autocomplete="current-password" />
        </label>
        <p v-if="loginError" class="form-error">{{ loginError }}</p>
        <button class="primary-button" type="submit" :disabled="loginLoading">
          <LoaderCircle v-if="loginLoading" class="spin" :size="18" />
          <LogIn v-else :size="18" />
          {{ loginLoading ? '正在登录' : '登录' }}
        </button>
      </form>

      <div class="demo-accounts" aria-label="Demo 账号">
        <span>Demo</span>
        <button type="button" @click="fillAccount('zhangsan')">张三</button>
        <button type="button" @click="fillAccount('lisi')">李四</button>
        <button type="button" @click="fillAccount('wanghr')">空间管理员</button>
      </div>
    </section>
  </main>

  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-mark small"><Sparkles :size="18" /></span>
        <strong>HRAgent AI</strong>
      </div>

      <button class="new-chat-button" type="button" @click="createConversation">
        <SquarePen :size="17" />
        新对话
      </button>

      <div class="history-label">最近对话</div>
      <nav class="conversation-list" aria-label="对话历史">
        <div
          v-for="conversation in sortedConversations"
          :key="conversation.id"
          :class="['conversation-item', { active: conversation.id === activeConversationId }]"
        >
          <button class="conversation-open" type="button" @click="activeConversationId = conversation.id">
            <MessageSquare :size="16" />
            <span>{{ conversation.title }}</span>
          </button>
          <button
            class="delete-chat-button"
            type="button"
            title="删除对话"
            aria-label="删除对话"
            @click.stop="deleteConversation(conversation.id)"
          >
            <Trash2 :size="15" />
          </button>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div v-if="profileMenuOpen" class="profile-menu">
          <div>
            <strong>{{ session.user.name }}</strong>
            <span>{{ session.user.workspaceName || '未加入企业' }}</span>
          </div>
          <button type="button" @click="signOut">
            <LogOut :size="16" />
            退出登录
          </button>
        </div>
        <button class="profile-button" type="button" @click="profileMenuOpen = !profileMenuOpen">
          <span class="avatar">
            <img v-if="session.user.avatarUrl" :src="session.user.avatarUrl" alt="" />
            <span v-else>{{ userInitial }}</span>
          </span>
          <span class="profile-copy">
            <strong>{{ session.user.name }}</strong>
            <small>{{ roleLabel }}</small>
          </span>
          <ChevronUp :size="16" />
        </button>
      </div>
    </aside>

    <section class="chat-page">
      <header class="chat-header">
        <div>
          <strong>HR 智能助手</strong>
          <span>{{ session.user.workspaceName }}</span>
        </div>
        <span :class="['service-status', { busy: sending }]">
          <span></span>
          {{ sending ? '正在处理' : '在线' }}
        </span>
      </header>

      <div ref="messagesElement" class="messages" aria-live="polite">
        <div v-if="activeConversation?.messages.length === 0" class="empty-state">
          <span class="assistant-emblem"><Sparkles :size="28" /></span>
          <h1>你好，{{ session.user.name }}</h1>
          <p>今天需要处理什么？</p>
          <div class="suggestions">
            <button v-for="suggestion in suggestions" :key="suggestion" type="button" @click="send(suggestion)">
              {{ suggestion }}
              <ArrowUpRight :size="16" />
            </button>
          </div>
        </div>

        <div v-else class="message-stream">
          <article
            v-for="message in activeConversation?.messages"
            :key="message.id"
            :class="['message-row', message.role]"
          >
            <div v-if="message.role !== 'user'" class="message-avatar">
              <Sparkles v-if="message.role === 'assistant'" :size="17" />
              <TriangleAlert v-else :size="17" />
            </div>
            <div class="message-body">
              <div class="message-meta">
                <strong>{{ message.role === 'user' ? session.user.name : message.role === 'error' ? '系统' : 'HRAgent' }}</strong>
                <span>{{ formatTime(message.createdAt) }}</span>
              </div>
              <div v-if="message.attachment" class="message-attachment">
                <span class="attachment-icon">
                  <ImageIcon v-if="message.attachment.image" :size="19" />
                  <FileText v-else :size="19" />
                </span>
                <span class="attachment-copy">
                  <strong>{{ message.attachment.name }}</strong>
                  <small>{{ formatFileSize(message.attachment.size) }}</small>
                </span>
              </div>
              <div
                v-if="message.content && message.role === 'assistant'"
                class="markdown-content"
                v-html="renderMarkdown(message.content)"
              ></div>
              <p v-else-if="message.content">{{ message.content }}</p>
            </div>
          </article>

          <article v-if="sending" class="message-row assistant pending">
            <div class="message-avatar"><Sparkles :size="17" /></div>
            <div class="message-body">
              <div class="message-meta"><strong>HRAgent</strong></div>
              <div class="typing" aria-label="正在生成回复"><span></span><span></span><span></span></div>
            </div>
          </article>
        </div>
      </div>

      <footer class="composer-area">
        <div class="composer">
          <div v-if="selectedFile" class="selected-attachment">
            <img v-if="selectedImageUrl" :src="selectedImageUrl" alt="待发送图片预览" />
            <span v-else class="attachment-icon"><FileText :size="20" /></span>
            <span class="attachment-copy">
              <strong>{{ selectedFile.name }}</strong>
              <small>{{ formatFileSize(selectedFile.size) }}</small>
            </span>
            <button type="button" title="移除附件" aria-label="移除附件" :disabled="sending" @click="clearAttachment">
              <X :size="17" />
            </button>
          </div>
          <div class="composer-row">
            <input
              ref="fileInputElement"
              class="file-input"
              type="file"
              accept=".jpg,.jpeg,.png,.pdf,.docx,.txt,image/jpeg,image/png,application/pdf,text/plain,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              @change="selectAttachment"
            />
            <button
              class="attach-button"
              type="button"
              title="上传文件或图片"
              aria-label="上传文件或图片"
              :disabled="sending"
              @click="fileInputElement?.click()"
            >
              <Paperclip :size="19" />
            </button>
            <textarea
              v-model="input"
              maxlength="1000"
              rows="1"
              placeholder="给 HRAgent 发送消息"
              :disabled="sending"
              @keydown="handleComposerKeydown"
            ></textarea>
            <button
              class="send-button"
              type="button"
              title="发送"
              aria-label="发送"
              :disabled="sending || (!input.trim() && !selectedFile)"
              @click="send()"
            >
              <Send :size="18" />
            </button>
          </div>
        </div>
        <p v-if="attachmentError" class="composer-error">{{ attachmentError }}</p>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import {
  ArrowUpRight,
  ChevronUp,
  FileText,
  Image as ImageIcon,
  LoaderCircle,
  LogIn,
  LogOut,
  MessageSquare,
  Paperclip,
  Send,
  Sparkles,
  SquarePen,
  Trash2,
  TriangleAlert,
  X
} from 'lucide-vue-next'
import { apiErrorMessage, login, logout, sendAttachment, sendMessage } from './api'
import type { AuthSession, ChatMessage, Conversation } from './types'

const AUTH_KEY = 'hragent_ai_auth'

const session = ref<AuthSession | null>(readSession())
const conversations = ref<Conversation[]>([])
const activeConversationId = ref('')
const input = ref('')
const sending = ref(false)
const loginLoading = ref(false)
const loginError = ref('')
const profileMenuOpen = ref(false)
const messagesElement = ref<HTMLElement | null>(null)
const fileInputElement = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const selectedImageUrl = ref('')
const attachmentError = ref('')
const loginForm = reactive({ username: 'zhangsan', password: '123456' })
const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
const ALLOWED_ATTACHMENT_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'pdf', 'docx', 'txt'])

marked.setOptions({
  breaks: true,
  gfm: true
})

const activeConversation = computed(() =>
  conversations.value.find((conversation) => conversation.id === activeConversationId.value)
)

const sortedConversations = computed(() =>
  [...conversations.value].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
)

const userInitial = computed(() => (session.value?.user.name || session.value?.user.username || 'U').slice(0, 1))

const roleLabel = computed(() => {
  if (session.value?.user.role === 'EMPLOYEE') return '员工'
  if (session.value?.user.role === 'MANAGER') return '主管'
  if (session.value?.user.role === 'HR') return '空间管理员'
  return '企业成员'
})

const suggestions = computed(() => {
  const items = ['查询我的年假余额', '我要申请年假', '查看我的请假进度']
  items.push(session.value?.user.role === 'MANAGER' ? '查看我的待审批请假' : '查询我的员工信息')
  return items
})

onMounted(() => {
  if (session.value) hydrateConversations()
})

watch(conversations, persistConversations, { deep: true })
watch(
  [activeConversationId, () => activeConversation.value?.messages.length, sending],
  () => scrollToBottom()
)

function readSession(): AuthSession | null {
  try {
    return JSON.parse(localStorage.getItem(AUTH_KEY) || 'null')
  } catch {
    return null
  }
}

function conversationStorageKey() {
  const user = session.value?.user
  return user ? `hragent_ai_conversations:${user.publicId}:${user.tenantId || 'none'}` : ''
}

function hydrateConversations() {
  const key = conversationStorageKey()
  try {
    conversations.value = JSON.parse(localStorage.getItem(key) || '[]')
  } catch {
    conversations.value = []
  }
  if (conversations.value.length === 0) {
    createConversation()
  } else {
    activeConversationId.value = sortedConversations.value[0].id
  }
}

function persistConversations() {
  const key = conversationStorageKey()
  if (key) localStorage.setItem(key, JSON.stringify(conversations.value))
}

function createId() {
  return crypto.randomUUID()
}

function createConversation() {
  const now = new Date().toISOString()
  const conversation: Conversation = {
    id: createId(),
    title: '新对话',
    messages: [],
    updatedAt: now
  }
  conversations.value.push(conversation)
  activeConversationId.value = conversation.id
  input.value = ''
  clearAttachment()
}

function deleteConversation(id: string) {
  conversations.value = conversations.value.filter((conversation) => conversation.id !== id)
  if (activeConversationId.value === id) {
    if (conversations.value.length === 0) createConversation()
    else activeConversationId.value = sortedConversations.value[0].id
  }
}

function fillAccount(username: string) {
  loginForm.username = username
  loginForm.password = '123456'
  loginError.value = ''
}

async function submitLogin() {
  if (!loginForm.username || !loginForm.password) {
    loginError.value = '请输入账号和密码'
    return
  }
  loginLoading.value = true
  loginError.value = ''
  try {
    const result = await login(loginForm.username, loginForm.password)
    if (!result.user.tenantId || result.user.membershipStatus !== 'ACTIVE' || !result.user.employeeProfileId) {
      throw new Error('当前账号没有可用的企业员工档案')
    }
    session.value = result
    localStorage.setItem(AUTH_KEY, JSON.stringify(result))
    hydrateConversations()
  } catch (error) {
    loginError.value = apiErrorMessage(error)
  } finally {
    loginLoading.value = false
  }
}

async function send(preset?: string) {
  if (!session.value || sending.value) return
  const text = (preset ?? input.value).trim()
  const file = selectedFile.value
  if (!text && !file) return
  if (!activeConversation.value) createConversation()

  const conversationId = activeConversationId.value
  const conversation = conversations.value.find((item) => item.id === conversationId)
  if (!conversation) return

  const now = new Date().toISOString()
  const userMessage: ChatMessage = {
    id: createId(),
    role: 'user',
    content: text,
    createdAt: now,
    attachment: file
      ? {
          name: file.name,
          size: file.size,
          type: file.type,
          image: isImageFile(file)
        }
      : undefined
  }
  conversation.messages.push(userMessage)
  conversation.updatedAt = now
  if (conversation.title === '新对话') {
    conversation.title = (text || file?.name || '文件对话').replace(/\s+/g, ' ').slice(0, 22)
  }
  input.value = ''
  clearAttachment()
  sending.value = true
  await scrollToBottom()

  try {
    const response = file
      ? await sendAttachment(session.value, file, text)
      : await sendMessage(session.value, text)
    appendMessage(conversationId, 'assistant', response.answer)
  } catch (error) {
    appendMessage(conversationId, 'error', apiErrorMessage(error))
  } finally {
    sending.value = false
  }
}

function selectAttachment(event: Event) {
  attachmentError.value = ''
  const inputElement = event.target as HTMLInputElement
  const file = inputElement.files?.[0]
  if (!file) return

  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (!ALLOWED_ATTACHMENT_EXTENSIONS.has(extension)) {
    attachmentError.value = '支持 JPG、JPEG、PNG、PDF、DOCX、TXT 文件'
    inputElement.value = ''
    return
  }
  if (file.size === 0) {
    attachmentError.value = '不能上传空文件'
    inputElement.value = ''
    return
  }
  if (file.size > MAX_ATTACHMENT_BYTES) {
    attachmentError.value = '文件不能超过 10 MB'
    inputElement.value = ''
    return
  }

  clearAttachment()
  selectedFile.value = file
  if (isImageFile(file)) selectedImageUrl.value = URL.createObjectURL(file)
}

function clearAttachment() {
  if (selectedImageUrl.value) URL.revokeObjectURL(selectedImageUrl.value)
  selectedImageUrl.value = ''
  selectedFile.value = null
  attachmentError.value = ''
  if (fileInputElement.value) fileInputElement.value.value = ''
}

function isImageFile(file: File) {
  return /\.(jpe?g|png)$/i.test(file.name)
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function renderMarkdown(content: string) {
  const html = marked.parse(content, { async: false }) as string
  return DOMPurify.sanitize(html, {
    USE_PROFILES: { html: true }
  })
}

function appendMessage(conversationId: string, role: 'assistant' | 'error', content: string) {
  const conversation = conversations.value.find((item) => item.id === conversationId)
  if (!conversation) return
  const now = new Date().toISOString()
  conversation.messages.push({ id: createId(), role, content, createdAt: now })
  conversation.updatedAt = now
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

async function scrollToBottom() {
  await nextTick()
  if (messagesElement.value) {
    messagesElement.value.scrollTop = messagesElement.value.scrollHeight
  }
}

async function signOut() {
  if (!session.value) return
  const currentSession = session.value
  session.value = null
  conversations.value = []
  activeConversationId.value = ''
  clearAttachment()
  localStorage.removeItem(AUTH_KEY)
  try {
    await logout(currentSession)
  } catch {
    // Local logout must remain available when the backend is offline.
  }
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value))
}
</script>
