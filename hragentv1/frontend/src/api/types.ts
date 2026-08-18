export type Role = 'NEW_HIRE' | 'EMPLOYEE' | 'MANAGER' | 'HR'
export type MembershipStatus = 'PENDING' | 'PENDING_PROFILE' | 'ACTIVE' | 'REJECTED' | 'LEFT' | 'DISABLED'
export type LeaveType = 'ANNUAL' | 'SICK' | 'PERSONAL' | 'MARRIAGE'
export type RequestStatus = 'PENDING_MANAGER' | 'PENDING_HR' | 'APPROVED' | 'REJECTED'
export type EmploymentCertificateType = 'STANDARD' | 'VISA'
export type CertificateLanguage = 'CHINESE' | 'ENGLISH' | 'BILINGUAL'
export type CertificateTemplateReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
export type CertificateRequestStatus =
  'PENDING_HR' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'GENERATED' | 'GENERATION_FAILED'
export type OnboardingRequestStatus = 'PENDING_HR' | 'APPROVED' | 'REJECTED'

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

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
  membershipStatus?: MembershipStatus
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
  status: MembershipStatus
  employeeProfileId?: number
  memberCount: number
}

export interface WorkspaceMember {
  membershipId: number
  accountId: number
  publicId: string
  username: string
  name: string
  email: string
  avatarUrl?: string
  role: Role
  status: MembershipStatus
  employeeProfileId?: number
  employeeNo?: string
  department?: string
  title?: string
  draftEmployeeNo?: string
  draftPhone?: string
  draftDepartment?: string
  draftTitle?: string
  draftManagerEmployeeNo?: string
  draftEntryDate?: string
  createdAt: string
}

export interface PlatformWorkspaceOverview {
  workspaceId: number
  name: string
  code: string
  active: boolean
  creatorPublicId?: string
  creatorName?: string
  memberCount: number
  aiCallCount: number
  apiCallCount: number
  createdAt: string
}

export interface PlatformApiUsageEntry {
  id: number
  method: string
  path: string
  statusCode: number
  createdAt: string
}

export interface PlatformOperationEntry {
  id: number
  action: string
  targetType?: string
  createdAt: string
}

export interface PlatformAiUsageEntry {
  id: number
  scenario: string
  provider?: string
  success: boolean
  createdAt: string
}

export interface PlatformWorkspaceDetail {
  workspace: PlatformWorkspaceOverview
  activeMemberCount: number
  pendingMemberCount: number
  leftMemberCount: number
  employeeCount: number
  managerCount: number
  adminCount: number
  apiCalls: PlatformApiUsageEntry[]
  operations: PlatformOperationEntry[]
  aiCalls: PlatformAiUsageEntry[]
}

export interface LeaveBalance {
  leaveType: LeaveType
  leaveTypeLabel: string
  totalDays: number
  usedDays: number
  remainingDays: number
}

export type CalendarDayType = 'WORK' | 'REST' | 'PENDING' | 'LEAVE'

export interface LeaveCalendarDay {
  date: string
  dayType: CalendarDayType
  label: string
  leaveType?: LeaveType
  leaveTypeLabel?: string
  requestStatus?: RequestStatus
}

export interface LeaveCalendar {
  year: number
  days: LeaveCalendarDay[]
}

export interface LeaveRequest {
  id: number
  employeeName: string
  managerName: string
  leaveType: LeaveType
  leaveTypeLabel: string
  startDate: string
  endDate: string
  days: number
  reason: string
  status: RequestStatus
  statusLabel: string
  aiRiskLevel: string
  aiSummary: string
  aiEvidence: string
  managerOpinion?: string
  hrOpinion?: string
  submittedAt: string
  managerReviewedAt?: string
  hrRecordedAt?: string
}

export interface KnowledgeArticle {
  id: number
  category: string
  title: string
  content: string
  source: string
  region: string
  publishedAt: string
  updatedAt: string
  reviewStatus: string
}

export interface DemoPolicy {
  sourceId: string
  sourceName: string
  sourceType: 'DEMO'
  title: string
  version: string
  region: string
  publishedAt: string
  effectiveAt: string
  summary: string
  content: string
  changeSummary: string
  contentHash: string
  sourceUpdatedAt: string
  updateAvailable: boolean
  disclaimer: string
}

export type PolicyReviewStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'

export interface PolicyMonitorCandidate {
  id: number
  sourceId: string
  sourceName: string
  sourceUrl: string
  title: string
  version: string
  region?: string
  publishedAt?: string
  effectiveAt?: string
  summary?: string
  content: string
  changeSummary?: string
  contentHash: string
  sourceUpdatedAt?: string
  detectedAt: string
  reviewStatus: PolicyReviewStatus
  reviewedAt?: string
  reviewOpinion?: string
  knowledgeArticleId?: number
}

export interface Employee {
  id: number
  employeeNo: string
  username: string
  accountPublicId?: string
  name: string
  role: Role
  department: string
  title: string
  email: string
  phone: string
  entryDate: string
  employeeStatus: string
  managerId?: number
  managerName?: string
  active: boolean
}

export interface DirectoryDepartment {
  id: number
  name: string
  code: string
  memberCount: number
}

export interface DirectoryOverview {
  departments: DirectoryDepartment[]
  employees: Employee[]
}

export interface EmployeeDetail {
  employee: Employee
  leaveDataVisible: boolean
  balances: LeaveBalance[]
  requests: LeaveRequest[]
}

export interface EmployeePersonalProfileSummary {
  employeeId: number
  employeeNo: string
  displayName: string
  legalName?: string
  role: Role
  department: string
  title: string
  employeeStatus?: string
  employmentType?: string
  workLocation?: string
  updatedAt?: string
  maintained: boolean
}

export interface EmployeePersonalProfile {
  employeeId: number
  employeeNo: string
  displayName: string
  legalName?: string
  englishName?: string
  role: Role
  department: string
  title: string
  email?: string
  phone?: string
  entryDate?: string
  employeeStatus?: string
  managerName?: string
  gender?: string
  birthDate?: string
  nationality?: string
  idType?: string
  idNumber?: string
  passportNumber?: string
  passportExpiryDate?: string
  employmentType?: string
  contractStartDate?: string
  contractEndDate?: string
  workLocation?: string
  monthlySalary?: number
  currency?: string
  homeAddress?: string
  emergencyContactName?: string
  emergencyContactPhone?: string
  updatedAt?: string
  maintained: boolean
}

export type EmployeePersonalProfileUpdate = Pick<EmployeePersonalProfile,
  'legalName' | 'englishName' | 'gender' | 'birthDate' | 'nationality' |
  'idType' | 'idNumber' | 'passportNumber' | 'passportExpiryDate' |
  'employmentType' | 'contractStartDate' | 'contractEndDate' | 'workLocation' |
  'monthlySalary' | 'currency' | 'homeAddress' | 'emergencyContactName' |
  'emergencyContactPhone'
>

export interface CertificateOption<T extends string> {
  value: T
  label: string
}

export interface EmploymentCertificateOptions {
  certificateTypes: CertificateOption<EmploymentCertificateType>[]
  languages: CertificateOption<CertificateLanguage>[]
}

export interface EmploymentCertificateTemplate {
  id: number
  name: string
  destinationCountry: string
  consulateName: string
  language: CertificateLanguage
  languageLabel: string
  sourceFileName: string
  fileSize: number
  active: boolean
  uploadedByEmployeeId: number
  reviewStatus: CertificateTemplateReviewStatus
  reviewStatusLabel: string
  reviewOpinion?: string
  reviewedAt?: string
  createdAt: string
  updatedAt: string
}

export interface EmploymentCertificateTemplatePreview {
  fileName: string
  fileSize: number
  readable: boolean
  hasPlaceholders: boolean
  canUpload: boolean
  placeholders: string[]
  unsupportedPlaceholders: string[]
  warnings: string[]
}

export interface EmploymentCertificateRequest {
  id: number
  employeeId: number
  employeeNo: string
  employeeName: string
  department?: string
  title?: string
  certificateType: EmploymentCertificateType
  certificateTypeLabel: string
  language: CertificateLanguage
  languageLabel: string
  purpose: string
  destinationCountry?: string
  consulateName?: string
  includeSalary: boolean
  remarks?: string
  status: CertificateRequestStatus
  statusLabel: string
  hrOpinion?: string
  submittedAt: string
  reviewedAt?: string
  profileReady: boolean
  missingProfileFields: string[]
  requestedTemplateId?: number
  requestedTemplateFileName?: string
  sourceTemplateFileName?: string
  generatedFileName?: string
  generationError?: string
  generatedAt?: string
  canCancel: boolean
  documentReady: boolean
}

export interface OnboardingRequest {
  id: number
  newHireId: number
  employeeNo: string
  accountName: string
  legalName: string
  phone: string
  personalEmail: string
  idNumberLast4: string
  plannedEntryDate: string
  department: string
  positionTitle: string
  managerName?: string
  workLocation: string
  emergencyContactName: string
  emergencyContactPhone: string
  bankName: string
  bankCardLast4: string
  highestEducation: string
  idDocumentPrepared: boolean
  bankCardPrepared: boolean
  educationCertificatePrepared: boolean
  photoPrepared: boolean
  officeSuppliesReceived: boolean
  remarks?: string
  status: OnboardingRequestStatus
  statusLabel: string
  hrOpinion?: string
  submittedAt: string
  reviewedAt?: string
}

export interface Department {
  id: number
  name: string
  code: string
  description: string
  active: boolean
  createdAt: string
}

export interface JobTitle {
  id: number
  name: string
  code: string
  description: string
  active: boolean
  createdAt: string
}

export interface ImportRowView {
  rowNumber: number
  valid: boolean
  action: string
  message: string
  values: string[]
}

export interface ImportResult {
  importType: string
  committed: boolean
  totalRows: number
  validRows: number
  failedRows: number
  rows: ImportRowView[]
}

export interface ImportBatch {
  id: number
  importType: string
  fileName: string
  totalRows: number
  successRows: number
  failedRows: number
  status: string
  message: string
  createdAt: string
}

export interface ApiKeyView {
  id: number
  name: string
  keyPrefix: string
  active: boolean
  createdAt: string
  lastUsedAt?: string
}

export interface ApiKeyCreateResponse {
  id: number
  name: string
  apiKey: string
  keyPrefix: string
}

export interface ApiCallLog {
  id: number
  apiKeyId: number
  method: string
  path: string
  statusCode: number
  message: string
  createdAt: string
}

export interface AuditLog {
  id: number
  actorName: string
  action: string
  targetType: string
  targetId: number
  detail: string
  createdAt: string
}

export interface AiCallRecord {
  id: number
  scenario: string
  provider: string
  promptText: string
  responseText: string
  success: boolean
  errorMessage?: string
  createdAt: string
}

export interface AiConfig {
  provider: string
  baseUrl: string
  model: string
  enabled: boolean
  apiKeyConfigured: boolean
  maskedApiKey: string
  credentialSource: 'DATABASE' | 'ENVIRONMENT' | 'NONE'
  updatedAt?: string
}

export interface AiConfigTestResult {
  success: boolean
  message: string
  provider: string
  model: string
  latencyMs: number
}
