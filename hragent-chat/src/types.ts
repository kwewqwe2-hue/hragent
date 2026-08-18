export type Role = 'NEW_HIRE' | 'EMPLOYEE' | 'MANAGER' | 'HR'

export interface UserProfile {
  id: number
  publicId: string
  username: string
  name: string
  email: string
  avatarUrl?: string
  platformAdmin: boolean
  tenantId?: number
  workspaceName?: string
  workspaceCode?: string
  membershipStatus?: string
  employeeProfileId?: number
  employeeNo?: string
  role?: Role
  department?: string
  title?: string
  managerId?: number
}

export interface WorkspaceSummary {
  workspaceId: number
  name: string
  code: string
  role: Role
  status: string
  employeeProfileId?: number
  memberCount: number
}

export interface AuthSession {
  token: string
  user: UserProfile
  workspaces: WorkspaceSummary[]
  workspaceId?: number
}

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'error'
  content: string
  createdAt: string
  attachment?: {
    name: string
    size: number
    type: string
    image: boolean
  }
}

export interface Conversation {
  id: string
  title: string
  messages: ChatMessage[]
  updatedAt: string
}
