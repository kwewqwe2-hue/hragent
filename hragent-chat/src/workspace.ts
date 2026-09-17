import type { AuthSession } from './types'
export const panelPaths = new Set(['/dashboard','/personal-info','/certificates','/onboarding','/my-leave','/directory','/employee-experience','/knowledge','/account','/workspace','/manager-approval','/hr-record','/hrssc','/employees','/all-records','/organization','/members','/imports','/ai-config','/logs','/open-platform','/api-center'])
export function workspaceUrl(path: string, embedded = false) {
  try {
    const root = new URL(window.location.origin); root.port = '5173'
    const url = new URL(path, root)
    if (url.origin !== root.origin || !panelPaths.has(url.pathname)) return ''
    if (embedded) url.searchParams.set('embed','agent')
    return url.href
  } catch { return '' }
}
export function saveSharedSession(session: AuthSession) {
  localStorage.setItem('hragent_ai_auth', JSON.stringify(session))
  localStorage.setItem('hragent_token', session.token)
  localStorage.setItem('hragent_user', JSON.stringify(session.user))
  localStorage.setItem('hragent_workspaces', JSON.stringify(session.workspaces || []))
  if(session.workspaceId || session.user.tenantId) localStorage.setItem('hragent_workspace_id', String(session.workspaceId || session.user.tenantId))
}
export function clearSharedSession(){for(const key of ['hragent_ai_auth','hragent_token','hragent_user','hragent_workspaces','hragent_workspace_id']) localStorage.removeItem(key)}
