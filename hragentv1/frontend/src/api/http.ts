import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import type { ApiResponse, EmploymentCertificateTemplate, EmploymentCertificateTemplatePreview, KnowledgeArticle } from './types'

export const http = axios.create({
  baseURL: '/api',
  timeout: 45000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('hragent_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const workspaceId = localStorage.getItem('hragent_workspace_id')
  if (workspaceId) {
    config.headers['X-Workspace-Id'] = workspaceId
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && body.success === false) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    const path=(response.config.url||'').replace(/^\/api/,'').split('?')[0]
    const method=(response.config.method||'get').toLowerCase()
    const submitted=method==='post'&&/^\/(leave|employment-certificates(?:\/with-template)?|onboarding|platform-access\/requests)$/.test(path)
    const reviewed=['post','put'].includes(method)&&/^\/(?:leave\/(?:hr\/\d+\/record|manager\/\d+\/review)|employment-certificates\/hr\/\d+\/(?:review|generate)|onboarding\/hr\/\d+\/review|lifecycle\/hr\/\d+\/review|platform-access\/(?:hr|manager)\/\d+\/review)$/.test(path)
    if(body?.success&&(submitted||reviewed)&&window.parent!==window){
      const parentOrigin=document.referrer?new URL(document.referrer).origin:window.location.origin
      window.parent.postMessage({type:'hragent:approval-change',stage:submitted?'submitted':'reviewed',workspaceId:localStorage.getItem('hragent_workspace_id')},parentOrigin)
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络错误'
    if (status === 401) {
      localStorage.removeItem('hragent_ai_auth')
      localStorage.removeItem('hragent_token')
      localStorage.removeItem('hragent_user')
      localStorage.removeItem('hragent_workspaces')
      localStorage.removeItem('hragent_workspace_id')
      router.push('/login')
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export async function getData<T = any>(url: string): Promise<T> {
  const response = await http.get<ApiResponse<T>>(url)
  return response.data.data
}

export async function postData<T = any>(url: string, data?: unknown): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, data)
  return response.data.data
}

export async function putData<T = any>(url: string, data?: unknown): Promise<T> {
  const response = await http.put<ApiResponse<T>>(url, data)
  return response.data.data
}

export async function deleteData<T = any>(url: string): Promise<T> {
  const response = await http.delete<ApiResponse<T>>(url)
  return response.data.data
}

export async function downloadBinary(url: string, fileName: string): Promise<void> {
  const response = await http.get<Blob>(url, { responseType: 'blob', timeout: 65000 })
  const magic=await response.data.slice(0,5).text()
  if(fileName.toLowerCase().endsWith('.pdf')&&magic!=='%PDF-')throw new Error('下载内容不是完整 PDF，请重试')
  if(fileName.toLowerCase().endsWith('.docx')&&!magic.startsWith('PK'))throw new Error('下载内容不是 Word 文件，请重试')
  const objectUrl = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 30000)
}

export async function uploadKnowledgeFile(
  file: File,
  metadata: { category: string; source?: string; region?: string; articleId?: number | null }
): Promise<KnowledgeArticle> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('category', metadata.category)
  if (metadata.source) formData.append('source', metadata.source)
  if (metadata.region) formData.append('region', metadata.region)
  if (metadata.articleId) formData.append('articleId', String(metadata.articleId))
  const response = await http.post<ApiResponse<KnowledgeArticle>>('/admin/knowledge/upload', formData)
  return response.data.data
}

export async function uploadCertificateTemplate(
  file: File,
  metadata: {
    name: string
    destinationCountry: string
    consulateName: string
    language: string
  }
): Promise<EmploymentCertificateTemplate> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('name', metadata.name)
  formData.append('destinationCountry', metadata.destinationCountry)
  formData.append('consulateName', metadata.consulateName)
  formData.append('language', metadata.language)
  const response = await http.post<ApiResponse<EmploymentCertificateTemplate>>(
    '/employment-certificate-templates',
    formData
  )
  return response.data.data
}

export async function previewCertificateTemplate(file: File): Promise<EmploymentCertificateTemplatePreview> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post<ApiResponse<EmploymentCertificateTemplatePreview>>(
    '/employment-certificate-templates/preview',
    formData
  )
  return response.data.data
}

export async function createCertificateWithTemplate(
  file: File,
  data: {
    templateName: string
    certificateType: string
    templateValues: Record<string,string>
    language: string
    purpose: string
    destinationCountry: string
    consulateName: string
    includeSalary: boolean
    remarks?: string
  }
): Promise<void> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('templateName', data.templateName)
  formData.append('certificateType', data.certificateType)
  formData.append('templateValues', JSON.stringify(data.templateValues))
  formData.append('language', data.language)
  formData.append('purpose', data.purpose)
  formData.append('destinationCountry', data.destinationCountry)
  formData.append('consulateName', data.consulateName)
  formData.append('includeSalary', String(data.includeSalary))
  if (data.remarks) formData.append('remarks', data.remarks)
  await http.post<ApiResponse<unknown>>('/employment-certificates/with-template', formData)
}
