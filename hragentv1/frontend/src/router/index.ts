import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/LoginView.vue') },
    { path: '/register', component: () => import('../views/RegisterView.vue') },
    {
      path: '/',
      component: () => import('../components/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/workspace' },
        { path: 'workspace', component: () => import('../views/WorkspaceView.vue') },
        { path: 'account', component: () => import('../views/AccountView.vue') },
        { path: 'personal-info', component: () => import('../views/PersonalInfoView.vue') },
        { path: 'certificates', component: () => import('../views/EmploymentCertificateView.vue') },
        { path: 'platform-admin', component: () => import('../views/PlatformAdminView.vue') },
        { path: 'dashboard', component: () => import('../views/DashboardView.vue') },
        { path: 'directory', component: () => import('../views/DirectoryView.vue') },
        { path: 'my-leave', component: () => import('../views/MyLeaveView.vue') },
        { path: 'manager-approval', component: () => import('../views/ManagerApprovalView.vue') },
        { path: 'hr-record', component: () => import('../views/HrRecordView.vue') },
        { path: 'all-records', component: () => import('../views/AllRecordsView.vue') },
        { path: 'employees', component: () => import('../views/EmployeesView.vue') },
        { path: 'members', component: () => import('../views/WorkspaceMembersView.vue') },
        { path: 'organization', component: () => import('../views/OrganizationView.vue') },
        { path: 'imports', component: () => import('../views/ImportCenterView.vue') },
        { path: 'api-center', component: () => import('../views/ApiCenterView.vue') },
        { path: 'open-platform', component: () => import('../views/OpenPlatformView.vue') },
        { path: 'ai-config', component: () => import('../views/AiConfigView.vue') },
        { path: 'knowledge', component: () => import('../views/KnowledgeView.vue') },
        { path: 'assistant', component: () => import('../views/AssistantView.vue') },
        { path: 'logs', component: () => import('../views/LogsView.vue') }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/login'
  }
  if ((to.path === '/login' || to.path === '/register') && auth.isLoggedIn) {
    if (auth.user?.platformAdmin) return '/platform-admin'
    return auth.hasActiveWorkspace ? '/dashboard' : '/workspace'
  }
  if (to.meta.requiresAuth && auth.isLoggedIn && !auth.hasActiveWorkspace
      && to.path !== '/workspace' && to.path !== '/account' && to.path !== '/platform-admin') {
    return '/workspace'
  }
  return true
})

export default router
