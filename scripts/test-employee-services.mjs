// Local demo acceptance: creates a labelled demo certificate; restores profile metadata and retires the test policy.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
const base = 'http://localhost:8080/api'
const evidence = new URL('../.artifacts/employee-services/', import.meta.url)
await mkdir(evidence, { recursive: true })
const results = []
async function login(username) {
  const response = await fetch(base + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password: '123456' }) })
  const payload = await response.json(); assert.equal(payload.success, true, 'Demo login failed')
  return payload.data
}
async function call(session, path, method = 'GET', body, expectedStatus = 200) {
  const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}`, 'X-Workspace-Id': String(session.user.tenantId) }, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000) })
  assert.equal(response.status, expectedStatus, `${method} ${path}: unexpected status`)
  if (expectedStatus !== 200) return
  if (response.headers.get('content-type')?.includes('application/json')) {
    const payload = await response.json(); assert.equal(payload.success, true, payload.message); return payload.data
  }
  return Buffer.from(await response.arrayBuffer())
}
const employee = await login('zhangsan'), hr = await login('wanghr'), another = await login('lisi')
const profile = await call(employee, '/employee-services/profile')
const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
let article, certificate
const policyBody = { title: '【演示验收】员工服务差旅条款', category: '差旅', content: '仅用于演示验收。差旅报销请准备订单与发票；具体金额标准由 HR 在正式制度中维护。', source: '演示验收制度 第三条（不是正式公司政策）', region: '上海', jobGrades: 'P3', workTypes: '研发', legalEntities: '演示验收合同主体', reviewStatus: 'APPROVED', publishedAt: today, effectiveFrom: today }
try {
  const status = await call(employee, '/web-chat/status'); assert.equal(status.businessServicesAvailable, true)
  for (const message of ['你好', '我还有几天年假', '我的提醒与待办', '我要开具收入证明']) {
    const started = Date.now(); const reply = await call(employee, '/web-chat/messages', 'POST', { message, conversationId: 'acceptance-services' })
    assert.ok(reply.answer.length > 10); assert.equal(reply.provider, 'employee-services')
    results.push({ check: message, success: true, latencyMs: Date.now() - started })
  }
  await call(employee, `/employee-services/hr/profiles/${profile.employeeId}`, 'PUT', {}, 403)
  results.push({ check: '员工不能修改制度匹配档案', success: true })
  await call(hr, `/employee-services/hr/profiles/${profile.employeeId}`, 'PUT', { location: '上海', jobGrade: 'P3', workType: '研发', legalEntity: '演示验收合同主体', probationEndDate: today, medicalCheckDeadline: today, annualLeaveExpiresAt: today })
  article = await call(hr, '/admin/knowledge', 'POST', policyBody)
  const policy = await call(employee, '/employee-services/policy/ask', 'POST', { message: '出差报销制度' })
  assert.ok(policy.citations.some(c => c.id === article.id && c.effectiveFrom === today))
  const unmatched = await call(another, '/employee-services/policy/ask', 'POST', { message: '出差报销制度' })
  assert.ok(!unmatched.citations.some(c => c.id === article.id))
  results.push({ check: '制度按员工四维属性匹配，携带出处和生效日期', success: true })
  const reminders = await call(employee, '/employee-services/reminders')
  for (const kind of ['PROBATION', 'MEDICAL']) assert.ok(reminders.some(r => r.kind === kind), kind)
  const balances = await call(employee, '/leave/balances')
  const annual = balances.find(b => b.leaveType === 'ANNUAL')
  assert.equal(reminders.some(r => r.kind === 'ANNUAL_LEAVE'), !!annual && Number(annual.remainingDays) > 0)
  const again = await call(employee, '/employee-services/reminders'); assert.equal(again.length, reminders.length)
  await call(another, `/employee-services/reminders/${reminders[0].id}`, 'PATCH', { action: 'DONE' }, 404)
  const updated = await call(employee, `/employee-services/reminders/${reminders[0].id}`, 'PATCH', { action: 'DONE' }); assert.equal(updated.status, 'DONE')
  const calendar = await call(employee, '/employee-services/reminders/calendar'); assert.ok(calendar.toString().includes('BEGIN:VALARM'))
  results.push({ check: '自动提醒去重、状态持久化、越权拒绝及日历导出', success: true })
  const checklist = await call(employee, '/employee-services/checklists', 'POST', { kind: 'HOUSING_FUND', origin: '北京', destination: '上海' })
  assert.ok(checklist.materials.length > 0); assert.ok(checklist.markdown.includes('上海'))
  await writeFile(new URL('checklist.md', evidence), checklist.markdown)
  await call(employee, '/employee-services/checklists', 'POST', { kind: 'INVALID', destination: '上海' }, 400)
  results.push({ check: '异地办理准备清单生成与参数校验', success: true })
  certificate = await call(employee, '/employment-certificates', 'POST', { certificateType: 'INCOME', language: 'CHINESE', purpose: '演示验收（仅用于验证下载）', includeSalary: false })
  assert.equal(certificate.includeSalary, true); assert.equal(certificate.status, 'PENDING_HR')
  await call(employee, `/employment-certificates/hr/${certificate.id}/review`, 'PUT', { approved: true }, 403)
  certificate = await call(hr, `/employment-certificates/hr/${certificate.id}/review`, 'PUT', { approved: true, opinion: '演示验收，正式用途需 HR 完成签章' })
  assert.equal(certificate.status, 'GENERATED', certificate.generationError)
  const document = await call(employee, `/employment-certificates/${certificate.id}/download`)
  assert.equal(document.subarray(0, 2).toString(), 'PK')
  await writeFile(new URL('income-certificate-demo.docx', evidence), document)
  await call(another, `/employment-certificates/${certificate.id}/download`, 'GET', undefined, 403)
  results.push({ check: '收入证明申请、HR 审批、DOCX 下载及文件权限', success: true, certificateId: certificate.id })
} finally {
  await call(hr, `/employee-services/hr/profiles/${profile.employeeId}`, 'PUT', profile)
  await call(employee, '/employee-services/reminders')
  if (article) await call(hr, `/admin/knowledge/${article.id}`, 'PUT', { ...policyBody, reviewStatus: 'DRAFT' })
}
await writeFile(new URL('acceptance.json', evidence), JSON.stringify(results, null, 2))
console.log(JSON.stringify({ passed: results.length, results }, null, 2))

