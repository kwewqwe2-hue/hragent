const assert=require('node:assert/strict')
const fs=require('node:fs/promises')
const path=require('node:path')
const base='http://localhost:8080/api'
;(async()=>{
 const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})
 const session=(await login.json()).data;assert.ok(session?.token)
 const headers={'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)}
 async function call(route,message){const r=await fetch(base+route,{headers,method:message?'POST':'GET',body:message?JSON.stringify({message}):undefined});const p=await r.json();assert.equal(r.ok,true,p.message);assert.equal(p.success,true);return p.data}
 const documents=await call('/employee-services/policy/documents');assert.equal(documents.length,4);assert.equal(documents.reduce((n,d)=>n+d.sections,0),142)
 const cases=[
 ['考勤有哪些规定？','考勤','HANDBOOK-2025'],
 ['休假有哪些规定？','假种','CHR-RS-17'],
 ['出差有哪些规定？','出差前','CHR-XZ-24'],
 ['报销需要注意什么？','费用类型和金额','CHR-CW-06'],
 ['薪酬福利有哪些规定？','固定收入','HANDBOOK-2025'],
 ['入职转正有哪些要求？','转正申请','HANDBOOK-2025'],
 ['年假有效期到什么时候','次年3月31日','CHR-RS-17'],
 ['员工去北京出差住宿标准','900元','CHR-XZ-24'],
 ['三亚12月旺季住宿标准','10月至次年4月','CHR-XZ-24'],
 ['出差餐费一天多少','150元','CHR-XZ-24'],
 ['出差回来几天报销','一周内','CHR-XZ-24'],
 ['普通员工出差谁审批','分管领导','CHR-XZ-24'],
 ['离沪出差报销谁审批','最终审批 总经理','CHR-CW-06'],
 ['员工借款2000元谁审批','2000元（含）以下','CHR-CW-06'],
 ['市内交通费500元谁审批','500元（含）以下','CHR-CW-06'],
 ['固定资产10万元谁审批','10万元（含）','CHR-CW-06'],
 ['代发薪酬20万元谁审批','财务部经理','CHR-CW-06'],
 ['事假3天谁批准','3天以内','CHR-RS-17'],
 ['婚假几天','法定节假日顺延','CHR-RS-17'],
 ['育儿假是自然年还是生日计算','周期年','CHR-RS-17'],
 ['工资几号发放','每月15日','HANDBOOK-2025'],
 ['试用期多久','六个月','HANDBOOK-2025'],
 ['病假需要什么材料','二级及以上医院','HANDBOOK-2025'],
 ['绩效考核申诉期限多久','5个工作日','HANDBOOK-2025'],
 ['培训费用离职必须返还吗','不能套用于所有培训','HANDBOOK-2025'],
 ['加班可以一律调休吗','300%','CHR-RS-17']]
 const results=[]
 for(const [q,fact,source]of cases){const r=await call('/employee-services/policy/ask',q);assert.match(r.status,/^DOCUMENT_/);assert.ok(r.answer.includes(fact),q+' missing '+fact);assert.ok(r.citations.some(c=>c.source.includes(source)),q);assert.ok(r.citations.every(c=>c.source.includes('PDF第')));results.push({question:q,status:r.status,sources:r.citations.map(c=>c.source),passed:true})}
 for(const [q] of cases){const r=await call('/web-chat/messages',q);const policy=await call('/employee-services/policy/ask',q);assert.equal(r.provider,'company-policy-documents',q);assert.equal(r.answer,policy.answer,q+' differs between policy and Agent AI')}
 const unknown=await call('/employee-services/policy/ask','量子补贴标准');assert.equal(unknown.citations.length,0)
 const salary=await call('/employee-services/policy/ask','工资几号发放');assert.equal(salary.citations[0].effectiveFrom,null);assert.match(salary.answer,/原文未载明/)
 await fs.writeFile('.artifacts/policies-2025/api-results.json',JSON.stringify({documents,results,chatQueries:cases.length,unknownRefused:true,missingDatePreserved:true},null,2));console.log('Company policy API acceptance passed: 26 policy questions, 26 matching Agent AI answers, sources and unknowns')
 const playwright=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'))
 const browser=await playwright.chromium.launch({channel:'msedge',headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000},locale:'zh-CN'});const errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(60000)
 try{
  await page.goto('http://localhost:5173/login');await page.locator('input').nth(0).fill('zhangsan');await page.locator('input').nth(1).fill('123456');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>!u.pathname.includes('login'))
  await page.goto('http://localhost:5173/employee-experience?section=policy');await page.getByRole('button',{name:'出差有哪些规定？',exact:true}).waitFor();assert.equal(await page.getByText(/已接入.*份企业文件/).count(),0)
  await page.getByRole('button',{name:'出差有哪些规定？',exact:true}).click();await page.locator('.es-policy-result').getByText('找到文件条款 · 个人适用性待核对',{exact:true}).waitFor();await page.locator('.es-policy-result').getByText('900元',{exact:false}).first().waitFor();await page.screenshot({path:'.artifacts/policies-2025/policy-desktop.png',fullPage:true})
  await page.getByLabel('政策问题',{exact:true}).fill('员工借款2000元谁审批？');await page.getByRole('button',{name:'查询制度',exact:true}).click();await page.locator('.es-policy-result').getByText('2000元（含）以下',{exact:false}).first().waitFor();await page.screenshot({path:'.artifacts/policies-2025/payment-desktop.png',fullPage:true})
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:'.artifacts/policies-2025/policy-mobile.png',fullPage:true});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);assert.ok((await page.locator('.employee-services').boundingBox()).width>=340)
  assert.deepEqual(errors,[]);await fs.writeFile('.artifacts/policies-2025/ui-results.json',JSON.stringify({passed:true,checks:['document count hidden','broad topic shortcuts','lodging answer and citations','payment thresholds','mobile readable width','no JavaScript errors']}));console.log('Company policy desktop/mobile acceptance passed')
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
