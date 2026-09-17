<template>
  <div class="employee-services">
    <header class="es-hero">
      <div><span class="es-eyebrow">HRAgent · 员工服务与关怀</span><h1>把人事小事，办得简单一点。</h1><p>从日常办理到重要时刻，为你找到有依据、有温度的支持。</p></div>
      <button class="es-outline" @click="reload" :disabled="loading">{{ loading ? '更新中…' : '刷新我的信息' }}</button>
    </header>
    <p v-if="error" class="es-alert error" role="alert">{{ error }}</p>
    <p v-if="notice" class="es-alert" role="status">{{ notice }}</p>
    <section class="es-profile" aria-label="制度匹配身份">
      <div><strong>{{ profile?.name || user.name }}</strong><span>工号 {{ profile?.employeeNo || '—' }}</span></div>
      <div v-for="field in identityFields" :key="field.key"><small>{{ field.label }}</small><span>{{ profile?.[field.key] || '待 HR 维护' }}</span></div>
    </section>
    <p v-if="profile?.missingAttributes.length" class="es-help">档案待补充：{{ profile.missingAttributes.join('、') }}。你仍可查阅所提供文件，个人适用标准需匹配档案后确认。</p>
    <nav class="es-tabs" aria-label="员工服务分类">
      <button v-for="item in tabs" :key="item.key" :class="{ selected: tab === item.key }" @click="tab = item.key">{{ item.label }}<span v-if="item.key === 'reminders' && activeReminders.length">{{ activeReminders.length }}</span></button>
      <button v-if="user.role === 'HR'" :class="{ selected: tab === 'admin' }" @click="openAdmin">HR 维护</button>
      <button v-if="user.role === 'HR'" :class="{ selected: tab === 'operations' }" @click="tab = 'operations'">组织运营</button>
    </nav>

    <section v-if="tab === 'policy'" class="es-panel">
      <div class="es-section-head"><div><span class="es-eyebrow">POLICY COPILOT</span><h2>与你相关的制度，带着依据回答。</h2><p>先看清条款与办理条件，再核对个人适用范围。企业制度和法律依据分别说明。</p></div><span class="es-badge">有出处 · 可追溯</span></div>
      <form class="es-search" @submit.prevent="askPolicy()"><input v-model="question" required maxlength="1000" aria-label="政策问题" placeholder="例如：我的出差住宿标准是多少？" /><button class="es-primary" :disabled="busy || !question.trim()">{{ busy ? '匹配中…' : '查询制度' }}</button></form>
      <div class="es-chips"><button v-for="q in policyQuestions" :key="q" @click="askPolicy(q)" :disabled="busy">{{ q }}</button></div>
      <div v-if="policy" class="es-policy-result">
        <h3>{{ policy.status === 'DOCUMENT_MATCHED' ? '已匹配企业文件' : policy.status === 'DOCUMENT_REFERENCE' ? '找到文件条款 · 个人适用性待核对' : policy.status === 'DOCUMENT_REVIEW' ? '条款版本需要核实' : policy.status === 'MATCHED' ? `找到 ${policy.citations.length} 条适用依据` : policy.status === 'HUMAN_SUPPORT' ? '优先获得人工支持' : policy.status === 'GUIDANCE' ? '服务与办理指引' : '还需要 HR 补充依据' }}</h3>
        <p v-if="policy.status !== 'MATCHED'" class="es-excerpt">{{ policy.answer }}</p>
        <button v-if="policy.status !== 'HUMAN_SUPPORT'" class="es-link" :disabled="busy" @click="reportUnresolved">没有解决我的问题，反馈给 HR</button>
        <p v-for="gap in policy.gaps.filter(g => !policy?.answer.includes(g))" :key="gap" class="es-help">{{ gap }}</p>
        <article v-for="citation in policy.citations" :key="citation.id" class="es-citation">
          <div class="es-row"><h3>{{ citation.title }}</h3><span class="es-badge">{{ citation.effectiveFrom ? `生效 ${citation.effectiveFrom}` : '生效日期未载明' }}</span></div>
          <details v-if="policy.status.startsWith('DOCUMENT_')"><summary>展开核对条款原文</summary><p class="es-excerpt">{{ citation.excerpt }}</p></details>
          <p v-else class="es-excerpt">{{ citation.excerpt }}</p>
          <div class="es-source"><strong>{{ citation.source }}</strong><span>发布 {{ citation.publishedAt || '未维护' }} · 有效至 {{ citation.effectiveTo || '未设截止日' }}</span></div>
          <p class="es-help">适用：{{ citation.region || '不限地区' }} / {{ citation.jobGrades || '不限职级' }} / {{ citation.workTypes || '不限工种' }} / {{ citation.legalEntities || '当前企业全员' }}</p>
          <a v-if="safeUrl(citation.sourceUrl)" :href="citation.sourceUrl" target="_blank" rel="noopener noreferrer">查看原文 ↗</a>
          <span v-else class="es-help">原文由 HR 保存在制度知识库，可按标题查阅。</span>
        </article>
        <p v-for="url in policyLawLinks" :key="url"><a :href="url" target="_blank" rel="noopener noreferrer">查看本回答引用的官方法律 / 政策解读 ↗</a></p>
      </div>
      <div v-else class="es-start"><strong>从一个具体问题开始</strong><p>你不必重复填写工号和身份信息。缺少生效日期或不适用的制度，不会被当成你的执行标准。</p></div>
      <details class="es-citation"><summary>上海法规与政策解读 · 官方查询入口</summary><p class="es-help">核验于 2026-09-08。检索时核对文号、适用地区、生效与失效日期；解读用于理解，执行依据仍需回到正文。这里是官方查询入口，不代表已实时检索全部政策。</p><p><a href="https://rsj.sh.gov.cn/tgwgfx_17726/index.html" target="_blank" rel="noopener">上海人社 · 规范性文件 ↗</a></p><p><a href="https://rsj.sh.gov.cn/tflfg_17253_17253/index.html" target="_blank" rel="noopener">上海人社 · 法律法规 ↗</a></p><p><a href="https://rsj.sh.gov.cn/tzcjd_17351/index.html" target="_blank" rel="noopener">上海人社 · 政策解读 ↗</a></p></details>
    </section>

    <section v-if="tab === 'certificates'" class="es-panel">
      <div class="es-section-head"><div><span class="es-eyebrow">ONE-STOP SERVICE</span><h2>证明办理</h2><p>提交用途 → HR 核对档案 → 生成文件 → 下载并按指引完成签章。</p></div></div>
      <form class="es-form" @submit.prevent="applyCertificate">
        <label>证明类型<select v-model="certificateType"><option value="STANDARD">在职证明</option><option value="INCOME">收入证明</option></select></label>
        <label>证明用途<input v-model.trim="purpose" required maxlength="200" placeholder="例如：银行贷款、租房、子女入学" /></label>
        <p class="es-help">{{ certificateType === 'INCOME' ? '收入证明使用本人档案中的税前月薪；仅你和有权限的 HR 可下载。' : '中文 DOCX，姓名、部门、岗位与入职日期来自员工档案。' }} 文档附盖章指引；企业电子签章由 HR 完成。</p>
        <button class="es-primary" :disabled="busy || !purpose.trim()">{{ busy ? '提交中…' : '提交证明申请' }}</button>
      </form>
      <div class="es-row"><h3>我的申请</h3><button class="es-link" @click="loadCertificates" :disabled="busy">刷新进度</button></div>
      <div v-if="!certificates.length" class="es-start">还没有证明申请。提交后可在这里跟踪进度并下载。</div>
      <article v-for="item in certificates" :key="item.id" class="es-item">
        <div><strong>{{ item.certificateTypeLabel }} <small>#{{ item.id }}</small></strong><p>{{ item.purpose }}</p><span class="es-badge">{{ item.statusLabel }}</span><p v-if="item.missingProfileFields.length" class="es-help">待补充：{{ item.missingProfileFields.join('、') }}</p><p v-if="item.hrOpinion" class="es-help">HR 意见：{{ item.hrOpinion }}</p><p v-if="item.generationError" class="es-help">文件生成失败，请联系 HR 重试。</p></div>
        <div class="es-actions"><button v-if="item.documentReady" class="es-primary" :disabled="busy" @click="downloadCertificate(item)">下载证明</button><button v-if="item.canCancel" class="es-outline" :disabled="busy" @click="cancelCertificate(item.id)">撤回申请</button></div>
      </article>
      <a :href="workbenchUrl + '/certificates'" target="_blank" rel="noopener">签证证明或 HR 审核，打开完整证明工作台 ↗</a>
    </section>

    <section v-if="tab === 'guides'" class="es-panel">
      <div class="es-section-head"><div><span class="es-eyebrow">办事有准备</span><h2>异地转移与落户清单</h2><p>选择目的地生成准备清单，附上已匹配的制度依据。</p></div></div>
      <form class="es-form es-grid" @submit.prevent="generateChecklist">
        <label>办理事项<select v-model="guideForm.kind"><option value="SOCIAL_SECURITY">社保转移接续</option><option value="HOUSING_FUND">公积金异地转移</option><option value="SETTLEMENT">落户材料准备</option></select></label>
        <label>转出地 / 当前所在地<input v-model.trim="guideForm.origin" maxlength="80" :required="guideForm.kind !== 'SETTLEMENT'" placeholder="例如：北京" /></label>
        <label>转入地 / 拟落户城市<input v-model.trim="guideForm.destination" required maxlength="80" placeholder="例如：上海" /></label>
        <button class="es-primary" :disabled="busy">{{ busy ? '生成中…' : '生成准备清单' }}</button>
      </form>
      <div v-if="checklist" class="es-checklist">
        <div class="es-row"><h3>{{ checklist.title }}</h3><button class="es-outline" @click="downloadText(checklist.markdown, checklist.title + '.md')">下载清单</button></div>
        <p class="es-alert">{{ checklist.status === 'NEEDS_HR' ? '尚无可确认的目的地指南。以下为准备提示，需向受理机构核实资格和完整材料。' : '这是办理前的准备清单；具体资格、原件及补件要求以受理机构对应路径为准。' }}</p>
        <h4>准备材料</h4><label v-for="material in checklist.materials" :key="material" class="es-check"><input type="checkbox" />{{ material }}</label>
        <h4>办理步骤</h4><ol><li v-for="step in checklist.steps" :key="step">{{ step }}</li></ol>
        <details v-for="source in checklist.citations" :key="source.id" class="es-citation"><summary>{{ source.title }} · 生效 {{ source.effectiveFrom }}</summary><p class="es-excerpt">{{ source.excerpt }}</p><p>{{ source.source }}</p><a v-if="safeUrl(source.sourceUrl)" :href="source.sourceUrl" target="_blank" rel="noopener">官方 / 制度原文 ↗</a></details>
      </div>
    </section>

    <section v-if="tab === 'reminders'" class="es-panel">
      <div class="es-section-head"><div><span class="es-eyebrow">重要日期，有人记得</span><h2>我的提醒与待办 <small>{{ activeReminders.length }}</small></h2><p>转正提前 30 天、续签提前 60 天、体检提前 30 天、年假到期提前 60 天提醒。</p></div><button class="es-outline" @click="downloadCalendar" :disabled="busy">导出日历</button></div>
      <p class="es-help">提醒每日自动检查，页面每分钟更新。标记“已处理”仅更新你的待办记录；合同、转正及预约结果仍以业务办理结果为准。</p>
      <div v-if="!reminders.length" class="es-start"><strong>目前没有触发的提醒</strong><p>HR 维护事件日期后，临近日期会自动出现。未维护的日期不会推算。</p></div>
      <article v-for="item in reminders" :key="item.id" class="es-item" :class="{ 'es-done': item.status === 'DONE' }">
        <div class="es-date"><strong>{{ item.dueDate.slice(5) }}</strong><span>{{ dueLabel(item) }}</span></div><div class="es-task-body"><strong>{{ item.title }}</strong><p>{{ item.description }}</p><small v-if="item.snoozedUntil && item.snoozedUntil > today">稍后提醒至 {{ item.snoozedUntil }}</small></div>
        <div class="es-actions"><a v-if="safeUrl(item.actionUrl)" :href="item.actionUrl" target="_blank" rel="noopener">前往预约 ↗</a><a v-if="item.kind === 'ANNUAL_LEAVE'" :href="workbenchUrl + '/my-leave'" target="_blank" rel="noopener">办理请假 ↗</a><button class="es-outline" :disabled="busy" @click="updateTask(item, item.status === 'DONE' ? 'OPEN' : 'DONE')">{{ item.status === 'DONE' ? '重新打开' : '标记已处理' }}</button><button v-if="item.status !== 'DONE'" class="es-link" :disabled="busy" @click="updateTask(item, 'SNOOZE')">明天提醒</button></div>
      </article>
      <details class="es-citation"><summary>查看提醒依据日期</summary><p>转正答辩：{{ profile?.probationEndDate || '未维护' }}</p><p>合同到期：{{ profile?.contractEndDate || '未维护' }}</p><p>体检预约截止：{{ profile?.medicalCheckDeadline || '未维护' }}</p><p>年假到期：{{ profile?.annualLeaveExpiresAt || '未维护' }}</p></details>
    </section>

    <section v-if="['onboarding', 'support', 'compliance', 'operations'].includes(tab)" class="es-panel"><EmployeeRelations :key="tab" :mode="tab" :user="user" :request="request" :workbench-url="workbenchUrl" /></section>

    <section v-if="tab === 'admin' && user.role === 'HR'" class="es-panel">
      <div class="es-section-head"><div><span class="es-eyebrow">HR 服务配置</span><h2>维护匹配属性与关键日期</h2><p>合同到期日沿用个人档案。这里维护其他服务属性和日期。</p></div><a :href="workbenchUrl + '/knowledge'" target="_blank" rel="noopener">维护制度范围与生效日期 ↗</a></div>
      <label class="es-employee-picker">选择员工<select v-model="selectedEmployee" :disabled="busy" @change="loadEmployee"><option value="">请选择员工</option><option v-for="employee in employees" :key="employee.id" :value="employee.id">{{ employee.employeeNo }} · {{ employee.name }}</option></select></label>
      <form v-if="editingProfile" class="es-form es-grid" @submit.prevent="saveEmployee">
        <label v-for="field in identityFields" :key="field.key">{{ field.label }}<input v-model="editingProfile[field.key]" :maxlength="field.key === 'legalEntity' ? 160 : 80" :placeholder="field.key === 'location' ? '城市名称，例如：上海' : '按员工正式档案填写'" /></label>
        <label>转正答辩日期<input v-model="editingProfile.probationEndDate" type="date" /></label><label>体检预约截止<input v-model="editingProfile.medicalCheckDeadline" type="date" /></label><label>当前年假到期日<input v-model="editingProfile.annualLeaveExpiresAt" type="date" /></label><label>体检预约链接<input v-model.trim="editingProfile.medicalBookingUrl" type="url" maxlength="1000" placeholder="https://…" /></label>
        <p class="es-help">日期留空表示尚未维护。更新日期会停用旧提醒，并按新日期生成待办。</p><button class="es-primary" :disabled="busy">{{ busy ? '保存中…' : '保存服务档案' }}</button>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import EmployeeRelations from './EmployeeRelations.vue'
type Request = (path: string, options?: { method?: string; body?: unknown; binary?: boolean }) => Promise<any>
interface Profile { employeeId: number; employeeNo: string; name: string; location: string; jobGrade: string; workType: string; legalEntity: string; probationEndDate: string; contractEndDate: string; medicalCheckDeadline: string; annualLeaveExpiresAt: string; medicalBookingUrl: string; missingAttributes: string[] }
interface Citation { id: number; title: string; excerpt: string; source: string; sourceUrl: string; publishedAt: string; effectiveFrom: string; effectiveTo: string; region: string; jobGrades: string; workTypes: string; legalEntities: string }
interface Policy { status: string; answer: string; citations: Citation[]; gaps: string[] }
interface Certificate { id: number; certificateTypeLabel: string; purpose: string; statusLabel: string; missingProfileFields: string[]; hrOpinion: string; generationError: string; documentReady: boolean; canCancel: boolean; generatedFileName: string }
interface Reminder { id: number; kind: string; title: string; dueDate: string; description: string; status: string; snoozedUntil: string; actionUrl: string }
interface Checklist { title: string; status: string; materials: string[]; steps: string[]; citations: Citation[]; markdown: string }
const props = defineProps<{ user: { id: number; name: string; role?: string }; request: Request; workbenchUrl: string; initialTab?: string }>()
const identityFields: { key: 'location' | 'jobGrade' | 'workType' | 'legalEntity'; label: string }[] = [{ key: 'location', label: '工作地点' }, { key: 'jobGrade', label: '职级' }, { key: 'workType', label: '工种' }, { key: 'legalEntity', label: '合同主体' }]
const tabs = [{ key: 'policy', label: '制度问答' }, { key: 'certificates', label: '证明办理' }, { key: 'guides', label: '办事清单' }, { key: 'reminders', label: '提醒与待办' }, { key: 'onboarding', label: '工作计划' }, { key: 'support', label: '支持与关怀' }, { key: 'compliance', label: '合规与申诉' }]
const policyQuestions = ['考勤有哪些规定？', '休假有哪些规定？', '出差有哪些规定？', '报销需要注意什么？', '薪酬福利有哪些规定？', '入职转正有哪些要求？']
const tab = ref(props.initialTab === 'care' ? 'onboarding' : props.initialTab || 'policy'), error = ref(''), notice = ref(''), loading = ref(false), busy = ref(false)
watch(() => props.initialTab, value => { if (value) tab.value = value === 'care' ? 'onboarding' : value })
const profile = ref<Profile>(), policy = ref<Policy>(), certificates = ref<Certificate[]>([]), reminders = ref<Reminder[]>([]), checklist = ref<Checklist>()
const policyLawLinks = computed(() => [...new Set(policy.value?.answer.match(/https:\/\/(?:rsj\.sh\.gov\.cn|www\.shanghai\.gov\.cn|www\.mohrss\.gov\.cn)\/[^\s)）]+/g) || [])].filter(url => safeUrl(url)))
const question = ref(''), purpose = ref(''), certificateType = ref('STANDARD')
const guideForm = reactive({ kind: 'SOCIAL_SECURITY', origin: '', destination: '' })
const employees = ref<{ id: number; employeeNo: string; name: string }[]>([]), selectedEmployee = ref<number | ''>(''), editingProfile = ref<Profile>()
const today = ref(chinaDate()), activeReminders = computed(() => reminders.value.filter(t => t.status === 'OPEN' && (!t.snoozedUntil || t.snoozedUntil <= today.value)))
let timer: ReturnType<typeof setInterval> | undefined
let alive = true
function chinaDate() { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date()) }
function safeUrl(url?: string) { try { return !!url && ['http:', 'https:'].includes(new URL(url).protocol) } catch { return false } }
function message(e: any) { return e?.response?.data?.message || e?.message || '请求失败，请重试' }
async function run(action: () => Promise<void>) { if (busy.value) return; busy.value = true; error.value = ''; notice.value = ''; try { await action() } catch (e) { error.value = message(e) } finally { busy.value = false } }
async function reload() {
  if (loading.value) return
  loading.value = true; error.value = ''
  const results = await Promise.allSettled([props.request('/employee-services/profile'), props.request('/employment-certificates/my'), props.request('/employee-services/reminders')])
  if (!alive) return
  if (results[0].status === 'fulfilled') profile.value = results[0].value
  if (results[1].status === 'fulfilled') certificates.value = results[1].value
  if (results[2].status === 'fulfilled') reminders.value = results[2].value
  for (const result of results) if (result.status === 'rejected') error.value = message(result.reason)
  today.value = chinaDate(); loading.value = false
}
async function askPolicy(preset?: string) { if (preset) question.value = preset; if (!question.value.trim()) return; await run(async () => { policy.value = await props.request('/employee-services/policy/ask', { method: 'POST', body: { message: question.value } }) }) }
async function loadCertificates() { await run(async () => { certificates.value = await props.request('/employment-certificates/my') }) }
async function applyCertificate() { await run(async () => { await props.request('/employment-certificates', { method: 'POST', body: { certificateType: certificateType.value, language: 'CHINESE', purpose: purpose.value, includeSalary: certificateType.value === 'INCOME' } }); notice.value = '申请已提交，等待 HR 审核。'; purpose.value = ''; certificates.value = await props.request('/employment-certificates/my') }) }
async function cancelCertificate(id: number) { await run(async () => { await props.request(`/employment-certificates/${id}/cancel`, { method: 'PUT' }); certificates.value = await props.request('/employment-certificates/my'); notice.value = '申请已撤回。' }) }
function saveBlob(blob: Blob, filename: string) { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
function downloadText(text: string, filename: string) { saveBlob(new Blob([text], { type: 'text/markdown;charset=utf-8' }), filename) }
async function downloadCertificate(item: Certificate) { await run(async () => saveBlob(await props.request(`/employment-certificates/${item.id}/download`, { binary: true }), item.generatedFileName || '证明.docx')) }
async function downloadCalendar() { await run(async () => saveBlob(await props.request('/employee-services/reminders/calendar', { binary: true }), '我的人事提醒.ics')) }
async function generateChecklist() { await run(async () => { checklist.value = await props.request('/employee-services/checklists', { method: 'POST', body: guideForm }) }) }
async function updateTask(task: Reminder, action: string) { await run(async () => { const tomorrow = new Date(today.value + 'T12:00:00Z'); tomorrow.setUTCDate(tomorrow.getUTCDate() + 1); await props.request(`/employee-services/reminders/${task.id}`, { method: 'PATCH', body: { action, until: action === 'SNOOZE' ? tomorrow.toISOString().slice(0, 10) : null } }); reminders.value = await props.request('/employee-services/reminders') }) }
function dueLabel(task: Reminder) { if (task.status === 'DONE') return '已处理'; const days = Math.round((Date.parse(task.dueDate) - Date.parse(today.value)) / 86400000); return days < 0 ? `已逾期 ${-days} 天` : days === 0 ? '今天截止' : `还有 ${days} 天` }
async function openAdmin() { tab.value = 'admin'; await run(async () => { employees.value = await props.request('/admin/employees') }) }
async function loadEmployee() { editingProfile.value = undefined; if (!selectedEmployee.value) return; await run(async () => { editingProfile.value = await props.request(`/employee-services/hr/profiles/${selectedEmployee.value}`) }) }
async function saveEmployee() { await run(async () => { if (!editingProfile.value) return; const body = { ...editingProfile.value }; for (const key of ['probationEndDate', 'medicalCheckDeadline', 'annualLeaveExpiresAt'] as const) if (!body[key]) (body as any)[key] = null; editingProfile.value = await props.request(`/employee-services/hr/profiles/${selectedEmployee.value}`, { method: 'PUT', body }); notice.value = '服务档案已保存。'; await reload() }) }
onMounted(() => { void reload(); timer = setInterval(() => { if (!document.hidden && !busy.value) void reload() }, 60000) })
onBeforeUnmount(() => { alive = false; if (timer) clearInterval(timer) })
async function reportUnresolved() { await run(async () => { notice.value = await props.request('/employee-relations/feedback', { method: 'POST', body: { question: question.value } }) }) }
</script>

<style scoped>
.employee-services{--es-ink:#20392f;--es-green:#257958;--es-border:#e1e9e4;max-width:1120px;margin:auto;padding:32px;color:var(--es-ink);font-family:Inter,"PingFang SC","Microsoft YaHei",sans-serif;text-align:left}.employee-services *{box-sizing:border-box}.employee-services button,.employee-services input,.employee-services select{font:inherit}.employee-services button,.employee-services a{cursor:pointer}.employee-services button{transition:background .16s,border-color .16s}.employee-services button:disabled{opacity:.5;cursor:wait}.employee-services button:focus-visible,.employee-services input:focus-visible,.employee-services select:focus-visible,.employee-services a:focus-visible{outline:3px solid #75bb9b;outline-offset:3px}.employee-services h1{font-size:30px;letter-spacing:-1px;line-height:1.4;margin:12px 0}.employee-services h2{font-size:22px;margin:8px 0 10px}.employee-services h3{font-size:17px;margin:12px 0}.employee-services h4{font-size:15px;margin:24px 0 12px}.employee-services p{font-size:14px;line-height:1.8;margin:6px 0 16px;color:#61736a}.employee-services a{color:var(--es-green);font-size:13px;text-decoration:none}.employee-services a:hover{text-decoration:underline}.es-hero{display:flex;gap:20px;align-items:center;justify-content:space-between;padding:12px 0 28px}.es-eyebrow{font-size:11px;letter-spacing:1.4px;font-weight:700;color:#447d65}.es-profile{display:grid;grid-template-columns:1fr 1fr .7fr .9fr 1.6fr;background:#edf4ef;border:1px solid var(--es-border);border-radius:14px;padding:20px;gap:20px}.es-profile>div{display:flex;flex-direction:column;gap:7px;min-width:0}.es-profile span{font-size:13px;overflow-wrap:anywhere}.es-profile small,.es-help{color:#748279;font-size:12px!important;line-height:1.8}.es-tabs{display:flex;gap:20px;border-bottom:1px solid var(--es-border);margin:26px 0 24px;overflow-x:auto}.es-tabs button{white-space:nowrap;padding:12px 2px 16px;border:0;border-bottom:3px solid transparent;color:#728077;background:transparent;font-size:14px}.es-tabs button.selected{color:var(--es-green);border-bottom-color:var(--es-green);font-weight:700}.es-tabs button span{font-size:11px;background:#e3f0e8;border-radius:12px;padding:2px 6px;margin-left:7px}.es-panel{border:1px solid var(--es-border);background:#fff;border-radius:16px;padding:28px;min-height:370px;box-shadow:0 8px 30px #213f3010}.es-section-head,.es-row{display:flex;align-items:center;justify-content:space-between;gap:16px}.es-badge{display:inline-block;white-space:nowrap;font-size:11px;padding:5px 9px;background:#edf5ef;border:1px solid #d9eadd;color:#447755;border-radius:6px}.es-search{display:flex;margin:20px 0 14px;gap:10px}.employee-services input:not([type=checkbox]),.employee-services select{width:100%;border:1px solid #d7e2db;border-radius:8px;padding:12px;background:#fbfdfb;color:#20392f;min-width:0}.es-primary,.es-outline{border-radius:8px;padding:11px 16px;white-space:nowrap;font-size:13px!important;font-weight:600;border:1px solid var(--es-green)}.es-primary{background:var(--es-green);color:#fff}.es-primary:hover{background:#1c6246}.es-outline{background:#fff;color:#426351;border-color:#d0ddd4}.es-outline:hover{background:#f2f7f3}.es-link{background:none;border:0;color:var(--es-green);padding:6px;font-size:12px!important}.es-chips{display:flex;gap:8px;flex-wrap:wrap}.es-chips button{background:#f5f7f5;border:1px solid #e4eae5;padding:7px 12px;border-radius:18px;color:#64766a;font-size:12px}.es-chips button:hover{border-color:#72a68b}.es-start{padding:44px 24px;text-align:center;color:#6e8075;background:#f8faf8;border-radius:12px;margin:28px 0}.es-start p{max-width:490px;margin:10px auto;font-size:13px}.es-policy-result{margin-top:28px}.es-citation{border:1px solid var(--es-border);border-radius:10px;padding:18px;margin:16px 0}.es-excerpt{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px!important;color:#334c3f!important;line-height:1.95!important}.es-source{display:flex;gap:12px;flex-wrap:wrap;font-size:12px;color:#6b7f70}.es-form{padding:22px;background:#f8faf8;border:1px solid var(--es-border);border-radius:12px;display:flex;flex-direction:column;gap:16px;margin:20px 0 28px}.es-form label,.es-employee-picker{display:flex;flex-direction:column;gap:8px;font-size:13px}.es-form>.es-primary{align-self:flex-start}.es-form p{margin:0}.es-grid{display:grid;grid-template-columns:1fr 1fr}.es-grid>.es-help{grid-column:1/-1}.es-grid>.es-primary{justify-self:start;align-self:end}.es-item{display:flex;align-items:center;gap:20px;padding:20px 0;border-bottom:1px solid var(--es-border)}.es-item:last-of-type{margin-bottom:20px}.es-item>div:first-child:not(.es-date){flex:1}.es-item p{margin:7px 0;font-size:13px}.es-item small{color:#809184}.es-actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap;justify-content:flex-end}.es-alert{border:1px solid #decda1;background:#fff9ed;padding:12px 16px;border-radius:8px;color:#806632!important;font-size:13px!important}.es-alert.error{background:#fff1ee;color:#a74835!important;border-color:#efc2b8}.es-check{display:flex;gap:10px;align-items:flex-start;font-size:14px;line-height:1.8;margin:10px 0}.es-check input{margin-top:6px;accent-color:var(--es-green)}.es-checklist li{font-size:14px;line-height:1.8;color:#61736a;padding:5px 0}.es-checklist ol{padding-left:20px}.es-date{background:#eef5ef;border-radius:10px;min-width:90px;text-align:center;padding:14px 8px;display:flex;flex-direction:column;gap:8px}.es-date strong{font-size:20px}.es-date span{font-size:11px;color:#7c8061}.es-task-body{flex:1}.es-done{opacity:.6}.es-employee-picker{max-width:420px;margin:20px 0}.employee-services details summary{cursor:pointer;font-size:13px;color:#447755}.employee-services details p:first-of-type{margin-top:14px}@media(max-width:760px){.employee-services{padding:20px 14px}.employee-services h1{font-size:24px}.es-hero,.es-section-head{align-items:flex-start;flex-direction:column}.es-profile{grid-template-columns:1fr 1fr;gap:16px}.es-profile>div:last-child{grid-column:1/-1}.es-panel{padding:18px}.es-grid{grid-template-columns:1fr}.es-item{flex-wrap:wrap}.es-actions{width:100%;justify-content:flex-start}.es-tabs{gap:15px}.es-search{flex-direction:column}.es-citation>.es-row{align-items:flex-start;flex-direction:column}.es-form{padding:16px}.es-row{flex-wrap:wrap}}
</style>
