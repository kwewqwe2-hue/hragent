const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const path = require('node:path')
const {chromium} = require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'))
;(async()=>{
 const base='http://localhost:8080/api'
 const session=(await (await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data
 for(const question of ['你好','我还有几天年假','年假有多少天','年假有多少天，出处是什么','我要开在职证明']) {
  const result=await (await fetch(base+'/web-chat/messages',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)},body:JSON.stringify({message:question})})).json()
  assert.equal(result.success,true,JSON.stringify(result)); const {answer,provider}=result.data
  assert.doesNotMatch(answer,/根据你提供的手册|相关条文如下|员工服务 →/)
  if(question==='你好') assert.ok(answer.length<100)
  if(question==='我还有几天年假') {assert.equal(provider,'personal-business');assert.match(answer,/天/);assert.doesNotMatch(answer,/参考规则/)}
  if(question==='年假有多少天') {assert.ok(answer.length<800);assert.doesNotMatch(answer,/条文标题|文号：/)}
  if(question==='我要开在职证明') assert.match(answer,/工作台.*在职证明/)
  console.log(JSON.stringify({question,answer,provider}))
 }
 const browser=await chromium.launch({headless:true,channel:'msedge'})
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message))
  await page.goto('http://localhost:5174/?view=services');await page.getByRole('button',{name:'登录',exact:true}).click()
  await page.locator('.composer textarea').waitFor()
  assert.equal(await page.getByRole('button',{name:'员工服务 · 自助办理',exact:true}).count(),0)
  await page.locator('.composer textarea').fill('我还有几天年假');await page.getByRole('button',{name:'发送',exact:true}).click()
  await page.locator('.message-row.assistant .markdown-content').filter({hasText:'天'}).waitFor()
  await fs.mkdir(path.resolve(__dirname,'../.artifacts'),{recursive:true});await page.screenshot({path:path.resolve(__dirname,'../.artifacts/concise-chat.png'),fullPage:true})
  await page.goto('http://localhost:5173/login');await page.locator('input').nth(0).fill('zhangsan');await page.locator('input').nth(1).fill('123456');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>!u.pathname.includes('login'))
  for(const route of ['my-leave','certificates','directory','employee-services']) {
   await page.goto('http://localhost:5173/'+route);await page.waitForLoadState('networkidle');assert.ok(!page.url().includes('/login'));assert.ok((await page.locator('body').innerText()).length>50)
  }
  await page.getByText('Policy Copilot',{exact:true}).waitFor();assert.deepEqual(errors,[])
  console.log('PASS: original chat, leave/certificate/directory/service pages, concise personalized reply, no JS errors')
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
