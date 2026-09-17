const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path')
const {chromium}=require(process.env.USERPROFILE+'/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright')
const base='http://localhost:8080/api'
async function login(username){const r=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})});assert.equal(r.status,200);return (await r.json()).data}
function headers(s){return {'Content-Type':'application/json',Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId)}}
async function api(s,url,body,expected=200){const r=await fetch(base+url,{method:body?'POST':'GET',headers:headers(s),body:body?JSON.stringify(body):undefined});const json=await r.json();assert.equal(r.status,expected,JSON.stringify(json));return json.data}
;(async()=>{
 const employee=await login('zhangsan'),hr=await login('wanghr'),other=await login('lisi');const errors=[]
 const browser=await chromium.launch({channel:'msedge',headless:true});const artifacts=path.resolve('.artifacts/lifecycle');await fs.mkdir(artifacts,{recursive:true})
 let id,finished=false
 try{
  const page=await browser.newPage({viewport:{width:1200,height:900}});page.on('pageerror',e=>errors.push(e.message));await page.goto('http://localhost:5174/');await page.getByRole('button',{name:'登录',exact:true}).click();await page.locator('textarea').waitFor()
  async function say(text){await page.locator('.composer textarea').fill(text);const response=page.waitForResponse(r=>r.url().endsWith('/web-chat/messages'));await page.getByRole('button',{name:'发送',exact:true}).click();const r=await response;const data=await r.json();assert.equal(r.status(),200,JSON.stringify(data));await page.waitForFunction(()=>!document.querySelector('.composer textarea').disabled);return data.data.answer}
  assert.match(await say('有哪些全周期服务'),/入职.*\n[\s\S]*离职后/)
  assert.match(await say('我要办理人事变更'),/开始填写/)
  await say('测试项目（仅验证系统流程）');await say('自动化验收：不修改真实员工档案');assert.match(await say('明天'),/确认提交/)
  const first=await say('确认提交');assert.match(first,/已提交/);id=Number(first.match(/#(\d+)/)[1]);assert.ok(id)
  assert.match(await say('确认提交'),/无需重复/)
  await api(employee,'/lifecycle/hr/queue',null,403)
  await api(other,`/lifecycle/${id}/file`,null,403)
  await api(hr,`/lifecycle/hr/${id}/review`,{action:'NEEDS_INFO',opinion:'验收测试：请确认不修改真实档案'})
  await page.getByRole('button',{name:'我的服务单',exact:true}).click();const card=page.locator('.requests article').filter({hasText:`#${id} ·`});await card.waitFor();assert.match(await card.innerText(),/待补充/)
  await card.locator('textarea').fill('确认：仅验证线上服务流，不修改真实档案')
  await card.getByRole('button',{name:'确认补充并重新提交'}).click();await page.waitForFunction(caseId=>[...document.querySelectorAll('.requests article')].some(x=>x.textContent.includes('#'+caseId+' ·')&&x.textContent.includes('待 HRSSC 审核')),id)
  const staff=await browser.newPage({viewport:{width:1350,height:950}});staff.on('pageerror',e=>errors.push(e.message));await staff.goto('http://localhost:5173/login');await staff.locator('input').nth(0).fill('wanghr');await staff.locator('input').nth(1).fill('123456');await staff.getByRole('button',{name:'登录',exact:true}).click();await staff.waitForURL(u=>!u.pathname.includes('login'));await staff.goto('http://localhost:5173/hrssc')
  const row=staff.locator('.el-table__row').filter({has:staff.locator('td').first().getByText(String(id),{exact:true})});await row.getByRole('button',{name:'查看处理'}).click();await staff.locator('.el-dialog textarea').fill('验收测试已核对，仅验证系统流程')
  await staff.getByRole('button',{name:'审核通过，开始办理'}).click();await staff.getByRole('button',{name:'确认已办理并交付'}).waitFor()
  await staff.locator('.el-dialog textarea').fill('验收完成：未执行真实人事变更');await staff.getByRole('button',{name:'确认已办理并交付'}).click();await staff.waitForFunction(()=>document.querySelector('.el-dialog')?.textContent.includes('当前状态：已办结'));finished=true
  await staff.screenshot({path:path.join(artifacts,'hrssc.png')})
  await page.getByRole('button',{name:'刷新',exact:true}).click();await page.waitForFunction(caseId=>[...document.querySelectorAll('.requests article')].some(x=>x.textContent.includes('#'+caseId+' ·')&&x.textContent.includes('已办结')),id);await page.screenshot({path:path.join(artifacts,'my-requests.png')})
  const salary=await api(employee,'/web-chat/messages',{message:'查询我的2026-08工资明细',conversationId:'payroll-validation'});assert.match(salary.answer,/工资|工资条/);assert.equal(salary.provider,'hrssc-lifecycle')
  const member=await browser.newPage({viewport:{width:910,height:794}});await member.goto('http://localhost:5173/login');await member.locator('input').nth(0).fill('lisi');await member.locator('input').nth(1).fill('123456');await member.getByRole('button',{name:'登录',exact:true}).click();await member.waitForURL(u=>!u.pathname.includes('login'));await member.goto('http://localhost:5173/employee-services');await member.getByText('工作成长与协作',{exact:true}).waitFor();assert.equal(await member.getByText('按阶段完成新人任务、理解术语并准备工具权限',{exact:true}).count(),0);await member.screenshot({path:path.join(artifacts,'employee-stage.png'),fullPage:true})
  assert.deepEqual(errors,[]);console.log(`PASS: conversation draft/confirmation, idempotency, HRSSC review/return/supplement/completion, owner and HR restrictions, payroll route, employee-stage copy; test service #${id} completed without real profile changes`)
 }finally{
  if(id&&!finished){const queue=await api(hr,'/lifecycle/hr/queue');const c=queue.find(x=>x.id===id);if(c?.status==='SUBMITTED')await api(hr,`/lifecycle/hr/${id}/review`,{action:'REJECTED',opinion:'自动化测试清理：未执行真实变更'})}
  await browser.close()
 }
})().catch(e=>{console.error(e);process.exitCode=1})
