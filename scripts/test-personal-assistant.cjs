const assert=require('node:assert/strict')
const path=require('node:path')
const fs=require('node:fs/promises')
const {chromium}=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'))
;(async()=>{
 const base='http://localhost:8080/api'
 let ready=false
 for(let attempt=0;attempt<40;attempt++) {
  try { if((await fetch(base+'/health',{signal:AbortSignal.timeout(3000)})).ok) {ready=true;break} } catch {}
  await new Promise(resolve=>setTimeout(resolve,3000))
 }
 assert.ok(ready,'Backend did not become ready')
 for(const username of ['zhangsan','lisi']) {
  const login=await (await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})})).json()
  assert.equal(login.success,true);const session=login.data
  async function call(route,body) {const r=await fetch(base+route,{method:body?'POST':'GET',headers:{'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)},body:body?JSON.stringify(body):undefined});const p=await r.json();assert.equal(p.success,true,JSON.stringify(p));return p.data}
  const balances=await call('/leave/balances');const annual=balances.find(b=>b.leaveType==='ANNUAL')
  for(const message of ['我还有多少年假','我还有几天年假','我还能休多少天年休假','查询我的年假余额']) {
   const reply=await call('/web-chat/messages',{message});assert.equal(reply.provider,'personal-business')
   assert.equal(reply.answer,annual?`你的年假还剩 ${Number(annual.remainingDays)} 天。`:'还没查到你的年假额度，请 HR 核对后补充。')
   assert.doesNotMatch(reply.answer,/手册|病假|婚假|参考规则/)
  }
  const leaves=await call('/leave/my');const progress=await call('/web-chat/messages',{message:'我的请假批了吗'})
  assert.equal(progress.provider,'personal-business');assert.match(progress.answer,leaves.length?/最近请假记录/:/没有请假记录/)
  const profile=await call('/web-chat/messages',{message:'我的部门是什么'});assert.equal(profile.provider,'personal-business');assert.equal(profile.answer,`部门：${session.user.department||'暂未维护'}`)
  const certificate=await call('/web-chat/messages',{message:'我的证明进度'});assert.equal(certificate.provider,'personal-business')
  console.log(`PASS ${username}: four annual leave phrasings match own ledger; leave status, department and certificate lookup`)
 }
 const browser=await chromium.launch({headless:true,channel:'msedge'})
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});await page.goto('http://localhost:5174/')
  await page.getByRole('button',{name:'登录',exact:true}).click();await page.locator('.composer textarea').fill('我还有多少年假')
  await page.getByRole('button',{name:'发送',exact:true}).click();await page.locator('.message-row.assistant .markdown-content').filter({hasText:'你的年假还剩'}).waitFor({timeout:60000})
  await fs.mkdir(path.resolve(__dirname,'../.artifacts'),{recursive:true});await page.screenshot({path:path.resolve(__dirname,'../.artifacts/personal-assistant.png')})
  console.log('PASS original chat: personal annual leave answer')
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
