import axios from 'axios'
import type { ApiResponse, AuthSession, UserProfile, WorkspaceSummary } from './types'

interface LoginPayload {
  token: string
  user: UserProfile
  workspaces: WorkspaceSummary[]
}

interface MessagePayload {
  answer: string
  provider: string
  requestId: string
}

const api = axios.create({
  baseURL: '/api',
  timeout: 135000
})

function authHeaders(session: AuthSession) {
  return {
    Authorization: `Bearer ${session.token}`,
    ...(session.workspaceId ? { 'X-Workspace-Id': String(session.workspaceId) } : {})
  }
}

function unwrap<T>(response: ApiResponse<T>): T {
  if (!response.success) {
    throw new Error(response.message || '请求失败')
  }
  return response.data
}

export async function login(username: string, password: string): Promise<AuthSession> {
  const response = await api.post<ApiResponse<LoginPayload>>('/auth/login', { username, password })
  const data = unwrap(response.data)
  return {
    token: data.token,
    user: data.user,
    workspaces: data.workspaces || [],
    workspaceId: data.user.tenantId
  }
}

export async function sendMessage(session: AuthSession, message: string): Promise<MessagePayload> {
  const response = await api.post<ApiResponse<MessagePayload>>(
    '/web-chat/messages',
    { message },
    { headers: authHeaders(session) }
  )
  return unwrap(response.data)
}

export async function sendAttachment(
  session: AuthSession,
  file: File,
  message: string
): Promise<MessagePayload> {
  const form = new FormData()
  form.append('file', file, file.name)
  if (message.trim()) form.append('message', message.trim())

  const response = await api.post<ApiResponse<MessagePayload>>(
    '/web-chat/attachments',
    form,
    {
      headers: authHeaders(session),
      timeout: 230000
    }
  )
  return unwrap(response.data)
}

export async function logout(session: AuthSession): Promise<void> {
  await api.post('/auth/logout', undefined, { headers: authHeaders(session) })
}

export function apiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED') {
      return '智能体响应超时，请稍后重试。'
    }
    return error.response?.data?.message || error.message || '网络请求失败'
  }
  return error instanceof Error ? error.message : '请求失败，请稍后重试。'
}
