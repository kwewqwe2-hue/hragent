import { defineStore } from 'pinia'
import { getData, postData } from '../api/http'
import type { UserProfile, WorkspaceSummary } from '../api/types'

interface LoginResponse {
  token: string
  user: UserProfile
  workspaces: WorkspaceSummary[]
}

interface RegisterPayload {
  username: string
  name: string
  email: string
  password: string
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('hragent_token') || '',
    user: JSON.parse(localStorage.getItem('hragent_user') || 'null') as UserProfile | null,
    workspaces: JSON.parse(localStorage.getItem('hragent_workspaces') || '[]') as WorkspaceSummary[]
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token && state.user),
    role: (state) => state.user?.role,
    hasActiveWorkspace: (state) => Boolean(
      state.user?.tenantId
      && state.user?.membershipStatus === 'ACTIVE'
      && state.user?.employeeProfileId
    )
  },
  actions: {
    async login(username: string, password: string) {
      const data = await postData<LoginResponse>('/auth/login', { username, password })
      this.applySession(data)
    },
    async register(payload: RegisterPayload) {
      const data = await postData<LoginResponse>('/auth/register', payload)
      this.applySession(data)
    },
    applySession(data: LoginResponse) {
      this.token = data.token
      this.user = data.user
      this.workspaces = data.workspaces || []
      localStorage.setItem('hragent_token', data.token)
      this.persistContext()
      if (data.user.tenantId) {
        localStorage.setItem('hragent_workspace_id', String(data.user.tenantId))
      } else {
        localStorage.removeItem('hragent_workspace_id')
      }
    },
    async refreshMe() {
      if (!this.token) return
      this.user = await getData<UserProfile>('/auth/me')
      this.persistContext()
    },
    async refreshWorkspaces() {
      if (!this.token) return
      this.workspaces = await getData<WorkspaceSummary[]>('/workspaces/mine')
      this.persistContext()
    },
    async selectWorkspace(workspaceId: number) {
      localStorage.setItem('hragent_workspace_id', String(workspaceId))
      await this.refreshMe()
      await this.refreshWorkspaces()
    },
    async leaveWorkspace(workspaceId: number) {
      await postData(`/workspaces/${workspaceId}/leave`)
      if (this.user?.tenantId === workspaceId) {
        localStorage.removeItem('hragent_workspace_id')
      }
      await this.refreshWorkspaces()
      await this.refreshMe()
      if (this.user?.tenantId && this.user.membershipStatus === 'ACTIVE') {
        localStorage.setItem('hragent_workspace_id', String(this.user.tenantId))
      } else {
        localStorage.removeItem('hragent_workspace_id')
      }
    },
    async updateProfile(payload: { name: string; email: string; avatarUrl?: string }) {
      this.user = await postData<UserProfile>('/auth/profile', payload)
      this.persistContext()
    },
    async changePassword(currentPassword: string, newPassword: string) {
      await postData('/auth/password', { currentPassword, newPassword })
    },
    persistContext() {
      localStorage.setItem('hragent_user', JSON.stringify(this.user))
      localStorage.setItem('hragent_workspaces', JSON.stringify(this.workspaces))
    },
    async logout() {
      try {
        await postData('/auth/logout')
      } finally {
        this.token = ''
        this.user = null
        this.workspaces = []
        localStorage.removeItem('hragent_token')
        localStorage.removeItem('hragent_user')
        localStorage.removeItem('hragent_workspaces')
        localStorage.removeItem('hragent_workspace_id')
      }
    }
  }
})
