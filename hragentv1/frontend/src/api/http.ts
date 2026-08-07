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
    return response
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络错误'
    if (status === 401) {
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
  const response = await http.get<Blob>(url, { responseType: 'blob' })
  const objectUrl = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(objectUrl)
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
  formData.append('language', data.language)
  formData.append('purpose', data.purpose)
  formData.append('destinationCountry', data.destinationCountry)
  formData.append('consulateName', data.consulateName)
  formData.append('includeSalary', String(data.includeSalary))
  if (data.remarks) formData.append('remarks', data.remarks)
  await http.post<ApiResponse<unknown>>('/employment-certificates/with-template', formData)
}
