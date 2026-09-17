<template>
  <main v-if="!session" class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-brand">
        <span class="brand-mark"><Sparkles :size="22" /></span>
        <div>
          <strong>HRAgent AI</strong>
          <span>你的员工服务入口</span>
        </div>
      </div>

      <div class="login-heading">
        <h1 id="login-title">登录</h1>
        <p>登录后，直接告诉我你要办理的事。</p>
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
        <button type="button" @click="fillAccount('chenchen')">新入职员工</button>
      </div>
    </section>
  </main>

  <div v-else class="app-shell" :class="{ 'mobile-menu-open': mobileMenuOpen, 'has-service-panel': panelUrl }">
    <button v-if="mobileMenuOpen" class="mobile-menu-backdrop" aria-label="关闭对话菜单" @click="mobileMenuOpen = false"></button>
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-mark small"><Sparkles :size="18" /></span>
        <strong>HRAgent AI</strong>
      </div>

      <div class="assistant-navigation"><button class="new-chat-button" type="button" @click="createConversation(); requestsOpen = false; mobileMenuOpen = false">
        <SquarePen :size="17" />
        新对话
      </button>

      <button class="new-chat-button" type="button" @click="requestsOpen = !requestsOpen; mobileMenuOpen = false">我的服务单</button>
      <a class="new-chat-button" :href="workspaceUrl('/dashboard')">前往工作台 ↗</a>
      </div><div class="history-label">最近对话</div>
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

    <section v-if="requestsOpen" class="chat-page requests-page"><LifecycleRequests :session="session" @close="requestsOpen = false" @open="openPanel" /></section>
    <section v-else class="chat-page">
      <header class="chat-header">
        <button class="mobile-menu-toggle" aria-label="打开对话菜单" @click="mobileMenuOpen = true">☰</button>
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
              <TypewriterReply
                v-if="message.content && message.role === 'assistant'"
                :html="renderMarkdown(message.content)"
                :animate="revealingReplies.has(message.id)"
                @link="handleBusinessLink"
                @complete="revealingReplies.delete(message.id)"
                @progress="followReply"
              />
              <p v-else-if="message.content">{{ message.content }}</p>
              <details v-if="message.role === 'assistant' && message.details && !revealingReplies.has(message.id)" class="policy-details">
                <summary>查看完整政策与适用说明</summary>
                <div class="markdown-content" @click="handleBusinessLink" v-html="renderMarkdown(message.details)"></div>
              </details>
              <div v-if="message.role === 'assistant' && message.actions?.length && !revealingReplies.has(message.id)" class="service-actions" aria-label="下一步办理选项">
                <template v-for="(action, index) in message.actions" :key="index">
                  <CertificateTemplateUpload v-if="action.type === 'certificate-template'" :session="session" :id="action.value" :label="action.label" :disabled="sending || message.id !== latestAssistantId" @busy="sending=$event" @result="onTemplateResult" />
                  <MedicalUpload v-if="action.type === 'medical-upload'" :session="session" :id="action.value" :label="action.label" :disabled="sending || message.id !== latestAssistantId" @busy="sending=$event" @result="appendMessage(activeConversationId, 'assistant', $event.answer, $event.actions, $event.details)" />
                  <button v-if="(action.type === 'workbench' || action.type === 'panel') && actionUrl(action.value)" type="button" @click="openPanel(action.label, action.value)">{{ action.label.replace('也可前往','打开').replace('前往','打开') }}</button>
                  <button v-else-if="action.type === 'message' || action.type === 'requests'" type="button" :disabled="sending || (action.type === 'message' && message.id !== latestAssistantId)" :class="{ 'service-confirm': action.value === '确认提交' }" @click="runServiceAction(action)">{{ action.label }}</button>
                </template>
              </div>
            </div>
          </article>

          <article v-if="sending" class="message-row assistant pending">
            <div class="message-avatar"><Sparkles :size="17" /></div>
            <div class="message-body">
              <div class="message-meta"><strong>HRAgent</strong></div>
              <div class="typing" aria-label="正在生成回复"><span></span><span></span><span></span></div>
              <p v-if="attachmentPending" role="status" class="attachment-progress">{{ attachmentPending }}</p>
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

    <section v-if="panelUrl" class="assistant-panel" aria-label="业务办理面板">
      <header><div><strong>{{ panelTitle }}</strong><small>办理结果与工作台同步</small></div><button @click="panelRevision++">刷新</button><button @click="panelUrl = ''" aria-label="关闭办理面板">关闭</button></header>
      <iframe ref="businessFrame" :key="panelUrl + panelRevision" :src="panelUrl" :title="panelTitle" referrerpolicy="same-origin"></iframe>
    </section>
    <ApprovalInbox ref="approvalInbox" :key="`${session.user.id}:${session.workspaceId}:${session.token}`" :session="session" @return="returnFromApproval" @open="openPanel" @requests="panelUrl='';requestsOpen=true" />
    <aside
      v-show="!panelUrl && !profileMenuOpen"
      ref="quickBoardElement"
      class="quick-board"
      :class="{ dragging: boardDragging, celebrating: kakaCelebrating }"
      :style="boardStyle"
      aria-label="Kaka 快捷对话"
    >
      <section ref="quickBoardPanel" v-if="quickPanelOpen" class="quick-board-panel">
        <header>
          <div>
            <strong>Kaka 快捷对话</strong>
            <span>{{ sending ? '当前回复完成后可开启快捷对话' : '点击后新建独立对话' }}</span>
          </div>
          <button type="button" title="收起快捷对话" aria-label="收起快捷对话" @click="quickPanelOpen = false">
            <X :size="16" />
          </button>
        </header>
        <div class="quick-board-actions">
          <button v-if="approvalInbox" type="button" class="quick-board-action" @click="quickPanelOpen = false; approvalInbox.openInbox()">审核与办理消息<ArrowUpRight :size="15" /></button>
          <button v-for="action in quickActions" :key="action.title" type="button" class="quick-board-action" :disabled="sending" @click="runQuickAction(action)">
            <span>{{ action.title }}</span>
            <ArrowUpRight :size="15" />
          </button>
        </div>
      </section>
      <button
        class="quick-board-handle"
        type="button"
        title="打开 Kaka 快捷对话，可拖动位置"
        aria-label="打开 Kaka 快捷对话，可拖动位置"
        @pointerdown="startBoardDrag"
        @pointerup="handleBoardPointerUp"
      >
        <img v-if="!kakaCelebrating" class="quick-board-static" :src="assetBase + 'kaka-3D.png'" alt="Kaka" draggable="false" />
        <img
          v-else
          :key="kakaImageKey"
          class="quick-board-celebration"
          :src="assetBase + 'kaka-thanks.gif'"
          alt=""
          aria-hidden="true"
          draggable="false"
        />
      </button>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
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
import {
  apiErrorMessage,
  isAuthenticationError,
  login,
  logout,
  sendAttachment,
  sendMessage,
  serviceRequest,
  validateSession
} from './api'
import type { AuthSession, ChatMessage, Conversation, ChatAction } from './types'
import LifecycleRequests from './components/LifecycleRequests.vue'
import MedicalUpload from './components/MedicalUpload.vue'
import CertificateTemplateUpload from './components/CertificateTemplateUpload.vue'
import ApprovalInbox from './components/ApprovalCenter.vue'
import { isSubmissionReceipt } from './approvals'
import TypewriterReply from './components/TypewriterReply.vue'
import { workspaceUrl, saveSharedSession, clearSharedSession } from './workspace'
const panelUrl = ref(''), panelTitle = ref(''), panelRevision = ref(0)
function openPanel(title: string, path: string) { const url = workspaceUrl(path, true); if(!url) return; panelTitle.value = title.replace('也可前往','').replace('前往',''); panelUrl.value = url; mobileMenuOpen.value = false; if(session.value) saveSharedSession(session.value) }
function handleBusinessLink(event: MouseEvent) {
 const anchor = (event.target as Element).closest('a'); if(!anchor) return
 if(workspaceUrl(anchor.href, true)) { event.preventDefault(); openPanel(anchor.textContent || '业务办理', anchor.href) }
}
const requestsOpen = ref(new URLSearchParams(window.location.search).get('view') === 'requests')
const mobileMenuOpen = ref(false)
const assetBase = import.meta.env.BASE_URL
const AUTH_KEY = 'hragent_ai_auth'
const session = ref<AuthSession | null>(readSession())
const conversations = ref<Conversation[]>([])
const activeConversationId = ref('')
const input = ref('')
const sending = ref(false)
const attachmentPending = ref('')
const revealingReplies = reactive(new Set<string>())
const loginLoading = ref(false)
const loginError = ref('')
const profileMenuOpen = ref(false)
const messagesElement = ref<HTMLElement | null>(null)
const fileInputElement = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const selectedImageUrl = ref('')
const attachmentError = ref('')
const quickPanelOpen = ref(false)
const boardDragging = ref(false)
const boardMoved = ref(false)
const quickBoardElement = ref<HTMLElement | null>(null)
const approvalInbox = ref<InstanceType<typeof ApprovalInbox> | null>(null)
const businessFrame = ref<HTMLIFrameElement | null>(null)
function onTemplateResult(response:{answer:string;provider?:string;actions?:ChatAction[]}){
  appendMessage(activeConversationId.value,'assistant',response.answer,response.actions)
  if(isSubmissionReceipt(response))approvalInbox.value?.refreshAfterSubmission()
}
function returnFromApproval(){panelUrl.value='';requestsOpen.value=false;quickPanelOpen.value=false;void scrollToBottom()}
function onApprovalChange(event:MessageEvent){
  if(!session.value||!panelUrl.value||event.source!==businessFrame.value?.contentWindow||event.origin!==new URL(panelUrl.value,window.location.href).origin)return
  if(event.data?.type!=='hragent:approval-change'||String(event.data.workspaceId)!==String(session.value.workspaceId??session.value.user.tenantId))return
  if(event.data.stage==='submitted')approvalInbox.value?.refreshAfterSubmission()
  else if(event.data.stage==='reviewed'){returnFromApproval();void approvalInbox.value?.load()}
}
onMounted(()=>window.addEventListener('message',onApprovalChange))
onBeforeUnmount(()=>window.removeEventListener('message',onApprovalChange))
const quickBoardPanel = ref<HTMLElement | null>(null)
const kakaCelebrating = ref(false)
const kakaImageKey = ref(0)
const kakaCelebrateTimer = ref<number | undefined>(undefined)
const loginForm = reactive({ username: 'zhangsan', password: '123456' })
const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
const ALLOWED_ATTACHMENT_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'pdf', 'docx', 'txt'])
const boardPosition = reactive(readBoardPosition())
const boardDragStart = reactive({ x: 0, y: 0, left: 0, bottom: 0 })
const quickActions = [
  { title: '今日人事提醒', prompt: '请帮我整理今天需要关注的人事事项和待办。' },
  { title: '入职第一天', prompt: '我是新员工，请告诉我入职第一天需要完成哪些事项。' },
  { title: '制度速读', prompt: '请用简洁、清晰的方式帮我理解公司制度中最需要注意的内容。' },
  { title: '请假前检查', prompt: '请在我申请请假前，帮我列出需要确认的信息和材料。' }
]

const boardStyle = computed(() => ({
  left: `${boardPosition.left}px`,
  bottom: `${boardPosition.bottom}px`
}))

const kakaImageSrc = computed(() => (kakaCelebrating.value
  ? ` ${assetBase}kaka-thanks.gif?v=${kakaImageKey.value}`.trim()
  : assetBase + 'kaka-3D.png'))

marked.setOptions({
  breaks: true,
  gfm: true
})

const activeConversation = computed(() =>
  conversations.value.find((conversation) => conversation.id === activeConversationId.value)
)
const latestAssistantId = computed(() => [...(activeConversation.value?.messages || [])].reverse().find(m => m.role === 'assistant')?.id)
function actionUrl(path: string) { return workspaceUrl(path) }
function runServiceAction(action: ChatAction) {
  if (sending.value) return
  if (action.type === 'requests') { requestsOpen.value = true; return }
  // A service choice must not accidentally upload a file left in the composer.
  if (action.type === 'message') { clearAttachment(); void send(action.value) }
}

const sortedConversations = computed(() =>
  [...conversations.value].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
)

const userInitial = computed(() => (session.value?.user.name || session.value?.user.username || 'U').slice(0, 1))

const roleLabel = computed(() => {
  if (session.value?.user.employeeStatus === 'LEFT') return '离职后服务'
  if (session.value?.user.role === 'NEW_HIRE') return '新入职员工'
  if (session.value?.user.role === 'EMPLOYEE') return '员工'
  if (session.value?.user.role === 'MANAGER') return '主管'
  if (session.value?.user.role === 'HR') return '空间管理员'
  return '企业成员'
})

const suggestions = computed(() => {
  if (session.value?.user.employeeStatus === 'LEFT') return ['补发离职证明', '我要调取档案', '我要申请劳动关系证明', '我要核对离职结算', '查看我的服务申请']
  if (session.value?.user.role === 'NEW_HIRE') {
    return ['我要办理入职手续', '入职需要准备什么', '查看我的入职进度', '入职当天有什么流程']
  }
  const items = ['查看所有员工服务', '查看我的工作计划', '查询我的年假余额', '我要申请请假', '查询我的工资条', '我要办理人事变更', '我要开在职证明', '我要咨询社保公积金', '有哪些全周期服务']
  items.push(session.value?.user.role === 'MANAGER' ? '查看我的待审批请假' : '查询我的员工信息')
  return items
})

onMounted(() => {
  if (session.value) {
    hydrateConversations()
    void verifyStoredSession()
  }
  window.addEventListener('resize', clampBoardPosition)
  window.addEventListener('storage', syncWorkspaceSession)
  void nextTick(clampBoardPosition)
  const preloaded = new Image()
  preloaded.src = assetBase + 'kaka-thanks.gif'
})

async function verifyStoredSession() {
  if (!session.value) return
  try {
    const current = session.value
    const user = await validateSession(current)
    if (session.value === current) { current.user = user; saveSharedSession(current) }
  } catch (error) {
    if (isAuthenticationError(error)) expireSession()
  }
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', clampBoardPosition)
  window.removeEventListener('storage', syncWorkspaceSession)
  if (kakaCelebrateTimer.value !== undefined) window.clearTimeout(kakaCelebrateTimer.value)
  stopBoardDrag()
})

watch(conversations, persistConversations, { deep: true })
watch([activeConversationId, () => session.value?.token], () => revealingReplies.clear())
watch(
  [activeConversationId, () => activeConversation.value?.messages.length, sending],
  () => scrollToBottom()
)

function syncWorkspaceSession(event: StorageEvent) {
 if(!['hragent_token','hragent_user','hragent_workspace_id'].includes(event.key || '')) return
 if(!localStorage.getItem('hragent_token')) { session.value = null; panelUrl.value = ''; return }
 const next = readSession()
 if(next && session.value && (next.workspaceId !== session.value.workspaceId || next.token !== session.value.token)) { session.value = next; panelUrl.value = ''; hydrateConversations(); void verifyStoredSession() }
}
function readSession(): AuthSession | null {
  try {
    const token = localStorage.getItem('hragent_token'), user = JSON.parse(localStorage.getItem('hragent_user') || 'null')
    if(token && user) return { token, user, workspaces: JSON.parse(localStorage.getItem('hragent_workspaces') || '[]'), workspaceId: Number(localStorage.getItem('hragent_workspace_id') || user.tenantId) || undefined }
    return JSON.parse(localStorage.getItem(AUTH_KEY) || 'null')
  } catch {
    return null
  }
}

function readBoardPosition() {
  try {
    const value = JSON.parse(localStorage.getItem('hragent_ai_quick_board_position') || 'null')
    if (value && Number.isFinite(value.left) && Number.isFinite(value.bottom)) {
      return { left: value.left, bottom: value.bottom }
    }
  } catch {
    // Use the default corner position when local storage is unavailable.
  }
  return { left: 20, bottom: 120 }
}

function persistBoardPosition() {
  localStorage.setItem('hragent_ai_quick_board_position', JSON.stringify(boardPosition))
}

function clampBoardPosition() {
  const rect = quickBoardElement.value?.getBoundingClientRect()
  const width = rect?.width || 118
  const height = rect?.height || 132
  const panelHeight = quickBoardPanel.value?.getBoundingClientRect().height || 0
  const totalHeight = height + (quickPanelOpen.value ? panelHeight + 10 : 0)
  boardPosition.left = Math.max(12, Math.min(boardPosition.left, window.innerWidth - width - 12))
  boardPosition.bottom = Math.min(Math.max(120, boardPosition.bottom), Math.max(12, window.innerHeight - totalHeight - 12))
}

function startBoardDrag(event: PointerEvent) {
  if (event.button !== 0) return
  const rect = quickBoardElement.value?.getBoundingClientRect()
  if (!rect) return
  event.preventDefault()
  boardDragging.value = true
  boardMoved.value = false
  boardDragStart.x = event.clientX
  boardDragStart.y = event.clientY
  boardDragStart.left = boardPosition.left
  boardDragStart.bottom = boardPosition.bottom
  window.addEventListener('pointermove', dragBoard)
  window.addEventListener('pointerup', stopBoardDrag)
}

function dragBoard(event: PointerEvent) {
  if (!boardDragging.value) return
  const deltaX = event.clientX - boardDragStart.x
  const deltaY = event.clientY - boardDragStart.y
  if (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4) boardMoved.value = true
  const rect = quickBoardElement.value?.getBoundingClientRect()
  const width = rect?.width || 118
  const height = rect?.height || 132
  const panelHeight = quickBoardPanel.value?.getBoundingClientRect().height || 0
  const totalHeight = height + (quickPanelOpen.value ? panelHeight + 10 : 0)
  boardPosition.left = Math.max(12, Math.min(boardDragStart.left + deltaX, window.innerWidth - width - 12))
  boardPosition.bottom = Math.min(Math.max(120, boardDragStart.bottom - deltaY), Math.max(12, window.innerHeight - totalHeight - 12))
}

function handleBoardPointerUp() {
  if (!boardMoved.value) {
    openQuickBoard()
  }
  boardMoved.value = false
}

function stopBoardDrag() {
  if (!boardDragging.value) return
  boardDragging.value = false
  window.removeEventListener('pointermove', dragBoard)
  window.removeEventListener('pointerup', stopBoardDrag)
  if (boardMoved.value) persistBoardPosition()
}

function playKakaCelebrate() {
  kakaCelebrating.value = true
  kakaImageKey.value += 1
  if (kakaCelebrateTimer.value !== undefined) window.clearTimeout(kakaCelebrateTimer.value)
  kakaCelebrateTimer.value = window.setTimeout(() => {
    kakaCelebrating.value = false
  }, 1200)
}

function openQuickBoard() {
  quickPanelOpen.value = true
  playKakaCelebrate()
  void nextTick(clampBoardPosition)
}

function runQuickAction(action: { title: string; prompt: string }) {
  if (!session.value || sending.value) return
  quickPanelOpen.value = false
  const conversation = createConversation()
  conversation.title = action.title
  void send(action.prompt)
}

function conversationStorageKey() {
  const user = session.value?.user
  return user ? `hragent_ai_conversations:${user.publicId}:${user.tenantId || 'none'}` : ''
}

function firstLoginWelcomeKey() {
  const publicId = session.value?.user.publicId
  return publicId ? `hragent_ai_first_welcome:${publicId}` : ''
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
  const welcomeKey = firstLoginWelcomeKey()
  if (session.value?.user.role === 'NEW_HIRE' && welcomeKey && !localStorage.getItem(welcomeKey)) {
    appendMessage(activeConversationId.value, 'assistant', [
      `欢迎加入 ${session.value.user.workspaceName || '企业空间'}！`,
      '',
      '你可以在这里询问入职流程、需要准备的材料和当前审核进度。',
      '入职登记入口：`http://localhost:5173/onboarding`',
      '工牌和办公用品请到直属上级处领取；如果还不清楚直属上级，请联系 HR。'
    ].join('\n'))
    localStorage.setItem(welcomeKey, '1')
  }
}

function persistConversations() {
  const key = conversationStorageKey()
  if (key) localStorage.setItem(key, JSON.stringify(conversations.value))
}

function createId() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }

  const bytes = new Uint8Array(16)
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes)
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256)
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const value = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`
}

function createConversation(): Conversation {
  requestsOpen.value = false
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
  return conversation
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
    if (!result.user.tenantId || (result.user.membershipStatus !== 'ACTIVE' && result.user.employeeStatus !== 'LEFT') || !result.user.employeeProfileId) {
      throw new Error('当前账号没有可用的企业员工档案')
    }
    session.value = result
    saveSharedSession(result)
    hydrateConversations()
  } catch (error) {
    loginError.value = apiErrorMessage(error)
  } finally {
    loginLoading.value = false
  }
}

async function send(preset?: string) {
  if (!session.value || sending.value) return
  revealingReplies.clear()
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
  attachmentPending.value = file ? '正在读取你提供的文件，稍等一下哦…' : ''
  const pendingTimer = file ? window.setTimeout(() => {
    attachmentPending.value = '文件还在处理中，请稍等；如果没有处理成功，我会在这里提醒你。'
  }, 12000) : undefined

  try {
    await scrollToBottom()
    const medical = [...conversation.messages].reverse().find(m=>m.role==='assistant')?.actions?.find(a=>a.type==='medical-upload')
    const medicalForm = new FormData(); if(file)medicalForm.append('file',file)
    const response = file
      ? medical ? await serviceRequest(session.value,`/lifecycle/${medical.value}/medical-record`,{method:'POST',body:medicalForm}) : await sendAttachment(session.value, file, text, conversationId)
      : await sendMessage(session.value, text, conversationId)
    appendMessage(conversationId, 'assistant', response.answer, response.actions, response.details, response.provider?.startsWith('er-'))
    if(isSubmissionReceipt(response))approvalInbox.value?.refreshAfterSubmission()
  } catch (error) {
    if (isAuthenticationError(error)) {
      persistConversations()
      expireSession()
      return
    }
    appendMessage(conversationId, 'error', apiErrorMessage(error))
  } finally {
    window.clearTimeout(pendingTimer)
    attachmentPending.value = ''
    sending.value = false
  }
}

function expireSession() {
  session.value = null
  clearSharedSession()
  loginError.value = '登录已失效，请重新登录'
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

function appendMessage(conversationId: string, role: 'assistant' | 'error', content: string, actions?: ChatAction[], details?: string, immediate=false) {
  const conversation = conversations.value.find((item) => item.id === conversationId)
  if (!conversation) return
  const now = new Date().toISOString()
  const id=createId()
  // Store the complete answer immediately; only its presentation is progressive.
  if(role==='assistant'&&!immediate&&conversationId===activeConversationId.value&&!/立即拨打|紧急风险|伤害自己|自杀/.test(content))revealingReplies.add(id)
  conversation.messages.push({ id, role, content, createdAt: now, actions, details })
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
function followReply() {
  const el=messagesElement.value
  if(el&&el.scrollHeight-el.scrollTop-el.clientHeight<180)el.scrollTop=el.scrollHeight
}

async function signOut() {
  if (!session.value) return
  const currentSession = session.value
  session.value = null
  conversations.value = []
  activeConversationId.value = ''
  clearAttachment()
  clearSharedSession()
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
