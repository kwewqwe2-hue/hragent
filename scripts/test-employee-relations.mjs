// Mutating legacy acceptance: use only a disposable test deployment, never the shared demo.
import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
const base = process.env.ER_ACCEPTANCE_BASE_URL
if (!base || ['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname) && ['8080','5173','5174'].includes(new URL(base).port)) {
  throw new Error('此验收会创建合成工单并修改配置，禁止在共享演示系统运行。请通过 ER_ACCEPTANCE_BASE_URL 指向独立、可销毁的测试部署。')
}
const output = new URL('../.artifacts/employee-relations/', import.meta.url)
await mkdir(output, { recursive: true })
const results = []
async function login(username) { const r = await fetch(base + '/auth/login', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({username,password:'123456'}) }); const b = await r.json(); assert.equal(b.success,true); return b.data }
async function call(s,path,method='GET',body,status=200) { const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',Authorization:`Bearer ${s.token}`,'X-Workspace-Id':String(s.user.tenantId)},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)}); const b=await r.json(); assert.equal(r.status,status,`${path}: ${b.message || r.status}`); if(status===200) { assert.equal(b.success,true); return b.data } }
function passed(check) { results.push({check,passed:true}); console.log('PASS: '+check) }
const employee=await login('zhangsan'), hr=await login('wanghr'), manager=await login('lisi')
const oldSettings=await call(hr,'/employee-relations/hr/settings')
const hrProfile=await call(hr,'/employee-services/profile')
const originalTasks=await call(employee,'/employee-relations/journey')
const testCases=[]
try {
  await call(employee,'/employee-relations/hr/settings','GET',undefined,403)
  await call(manager,'/employee-relations/hr/insights','GET',undefined,403)
  await call(hr,'/employee-relations/hr/settings','PUT',{...oldSettings,investigatorIds:[]})
  await call(employee,'/employee-relations/cases','POST',{kind:'COMPLAINT',anonymous:true,facts:'【自动验收】未配置受理人',consent:true},400)
  const urgentNoRoute=await call(employee,'/web-chat/messages','POST',{message:'【自动验收】我不想活了',conversationId:'er-acceptance'})
  assert.match(urgentNoRoute.answer,/没有创建或转交工单/); assert.match(urgentNoRoute.answer,/12356/)
  passed('未配置调查人员时不收集申诉、不虚报接管；提供紧急支持')
  const care=await call(employee,'/web-chat/messages','POST',{message:'最近工作压力有点大'})
  assert.equal(care.provider,'er-verified-guidance'); assert.match(care.answer,/国家卫生健康委/)
  for (const message of ['新人第一周如何安排','竞业限制需要注意什么','工伤怎么申请','调岗调薪有什么注意事项','离职交接怎么准备','如何预防职场霸凌']) {
    const r=await call(employee,'/web-chat/messages','POST',{message}); assert.equal(r.provider,'er-verified-guidance'); assert.match(r.answer,/来源|核验日期/)
  }
  passed('关怀、入职、离职、竞业、工伤和调岗咨询走可追溯的服务指引')
  const changed=await call(employee,'/employee-relations/journey/'+originalTasks[0].key,'PUT',{enabled:!originalTasks[0].done})
  assert.equal(changed[0].done,!originalTasks[0].done)
  assert.equal((await call(employee,'/employee-relations/journey'))[0].done,!originalTasks[0].done)
  passed('新人任务进度可保存并重新读取')
  await call(hr,'/employee-relations/hr/settings','PUT',{...oldSettings,investigatorIds:[hrProfile.employeeId]})
  const receipt=await call(employee,'/employee-relations/cases','POST',{kind:'COMPLAINT',anonymous:true,facts:'【自动验收】匿名事实与权限测试，无真实事件',occurredAt:'测试时间',evidence:'合成数据',consent:true})
  testCases.push(receipt.caseInfo.id); assert.equal(receipt.caseInfo.anonymous,true); assert.equal(receipt.receipt.length,43)
  const mine=await call(employee,'/employee-relations/cases'); assert.ok(!mine.some(c=>c.id===receipt.caseInfo.id))
  await call(manager,'/employee-relations/cases/'+receipt.caseInfo.id+'/followup','POST',{message:'无凭证越权'},404)
  await call(manager,'/employee-relations/hr/cases/'+receipt.caseInfo.id,'GET',undefined,403)
  await call(employee,'/employee-relations/cases/lookup','POST',{receipt:'a'.repeat(43)},404)
  const lookup=await call(employee,'/employee-relations/cases/lookup','POST',{receipt:receipt.receipt}); assert.equal(lookup.employee,'匿名员工')
  passed('匿名工单不出现在账号历史；查询码校验和主管越权防护有效')
  await call(employee,'/employee-relations/cases/'+receipt.caseInfo.id+'/followup','POST',{receipt:receipt.receipt,message:'【自动验收】补充合成线索'})
  await call(hr,'/employee-relations/hr/cases/'+receipt.caseInfo.id,'PATCH',{status:'IN_PROGRESS',reply:'【自动验收】已接单，待核实'})
  const replied=await call(employee,'/employee-relations/cases/lookup','POST',{receipt:receipt.receipt}); assert.match(replied.reply,/已接单/)
  passed('匿名补充、HR 接单及人工回复形成闭环')
  const urgent=await call(employee,'/web-chat/messages','POST',{message:'【自动验收】我想伤害自己'})
  assert.equal(urgent.provider,'er-human-support'); assert.match(urgent.answer,/等待人工接单/)
  const urgentCase=(await call(employee,'/employee-relations/cases')).find(c=>c.kind==='URGENT' && c.status!=='RESOLVED')
  assert.ok(urgentCase); testCases.push(urgentCase.id)
  const held=await call(employee,'/web-chat/messages','POST',{message:'【自动验收】查询我的年假余额'})
  assert.equal(held.provider,'er-human-support'); assert.match(held.answer,/补充已加密/)
  await call(hr,'/employee-relations/hr/cases/'+urgentCase.id,'PATCH',{status:'RESOLVED',reply:'【自动验收】合成场景验证结束，无真实危机'})
  const resumed=await call(employee,'/web-chat/messages','POST',{message:'查询我的年假余额'})
  assert.equal(resumed.provider,'employee-services')
  passed('危机识别优先于 AI 与办理；结案前持续转人工，结案后恢复服务')
  await call(employee,'/employee-relations/feedback','POST',{question:'差旅报销制度的某些细节未解决'})
  const gaps=await call(hr,'/employee-relations/hr/gaps'); assert.ok(gaps.some(g=>g.topic==='差旅与报销' && g.status==='OPEN'))
  const insights=await call(hr,'/employee-relations/hr/insights'); assert.ok(insights.topics.every(t=>t.participants>=5))
  assert.ok(!JSON.stringify(insights).includes('employeeId'))
  const count=await call(hr,'/employee-relations/hr/knowledge-drafts','POST'); assert.ok(count>=0)
  const knowledge=await call(hr,'/admin/knowledge'); const added=knowledge.filter(a=>a.category==='员工关系服务参考'); assert.ok(added.length>=8); assert.ok(added.every(a=>a.reviewStatus==='DRAFT' && !a.effectiveFrom))
  assert.equal(await call(hr,'/employee-relations/hr/knowledge-drafts','POST'),0)
  passed('知识缺口聚合、隐私门槛及八条知识草稿幂等导入')
} finally {
  for(const id of testCases) { try { await call(hr,'/employee-relations/hr/cases/'+id,'PATCH',{status:'RESOLVED',reply:'【自动验收】测试工单结束，无真实事件'}) } catch {} }
  await call(employee,'/employee-relations/journey/'+originalTasks[0].key,'PUT',{enabled:originalTasks[0].done})
  await call(hr,'/employee-relations/hr/settings','PUT',oldSettings)
  await writeFile(new URL('api-results.json',output),JSON.stringify({results,restoredSettings:true},null,2))
}
console.log(`${results.length} employee-relations acceptance groups passed`)
