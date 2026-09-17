import axios from 'axios'
import type { ApiResponse, AuthSession, UserProfile, WorkspaceSummary, ChatAction } from './types'

interface LoginPayload {
  token: string
  user: UserProfile
  workspaces: WorkspaceSummary[]
}

interface MessagePayload {
  details?: string
  answer: string
  provider: string
  requestId: string
  actions?: ChatAction[]
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

export async function validateSession(session: AuthSession): Promise<UserProfile> {
  const response = await api.get<ApiResponse<UserProfile>>('/auth/me', { headers: authHeaders(session) })
  return unwrap(response.data)
}

export async function serviceRequest(session: AuthSession, path: string, options: { method?: string; body?: unknown; binary?: boolean } = {}) {
  const response = await api.request({ url: path, method: options.method || 'GET', data: options.body,
    headers: authHeaders(session), responseType: options.binary ? 'blob' : 'json' })
  return options.binary ? response.data : unwrap(response.data)
}

export async function sendMessage(session: AuthSession, message: string, conversationId?: string): Promise<MessagePayload> {
  const response = await api.post<ApiResponse<MessagePayload>>(
    '/web-chat/messages',
    { message, conversationId },
    { headers: authHeaders(session) }
  )
  return unwrap(response.data)
}

export async function sendAttachment(
  session: AuthSession,
  file: File,
  message: string,
  conversationId?: string
): Promise<MessagePayload> {
  const form = new FormData()
  form.append('file', file, file.name)
  if (message.trim()) form.append('message', message.trim())
  if (conversationId) form.append('conversationId', conversationId)

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

export async function uploadLifecycleMaterials(session: AuthSession, id: number, file: File) {
  const form = new FormData(); form.append('file', file)
  return unwrap((await api.post(`/lifecycle/${id}/materials`, form, {headers: authHeaders(session)})).data)
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

export function isAuthenticationError(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401
}

export async function certificatePdf(session: AuthSession,id:number){
 const response=await api.get<Blob>(`/employment-certificates/${id}/pdf`,{headers:authHeaders(session),responseType:'blob',timeout:65000})
 if(!(response.data instanceof Blob)||(await response.data.slice(0,5).text())!=='%PDF-')throw new Error('证明文件内容异常，请重新获取')
 const signed=response.headers['x-certificate-signed']==='true'
 return {blob:response.data,signed,name:`在职证明-${id}-${signed?'已签章':'未签章预览'}.pdf`}
}
