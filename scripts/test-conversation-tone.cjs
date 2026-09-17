const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs')
;(async()=>{
 const base='http://localhost:5173';const login=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});const session=(await login.json()).data
 async function ask(message,conversationId='tone-'+Date.now()) {const r=await fetch(base+'/api/web-chat/messages',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+session.token,'X-Workspace-Id':String(session.user.tenantId)},body:JSON.stringify({message,conversationId})});assert.equal(r.status,200);return (await r.json()).data}
 const cid='tone-guidance-'+Date.now();const broad=await ask('我想了解年假',cid);assert.equal(broad.provider,'conversation-guide');assert.ok(broad.answer.length<150);assert.equal(broad.actions.length,3)
 const follow=await ask('有效期',cid);assert.equal(follow.provider,'company-policy-documents');assert.match(follow.answer,/3月31日/);assert.match(follow.answer,/核对/);assert.match(follow.details,/PDF第3页/);assert.match(follow.details,/2025-10-14/);assert.ok(follow.answer.length<follow.details.length)
 assert.doesNotMatch(follow.answer,/满1年不满10年/)
 const full=await ask('查看原文',cid);assert.match(full.answer,/PDF第3页/);assert.equal(full.details,null)
 assert.equal((await ask('你好')).provider,'conversation-guide');assert.match((await ask('谢谢')).answer,/不客气/)
 const travel='tone-travel-'+Date.now();assert.match((await ask('出差住宿标准是什么',travel)).answer,/哪个城市/);assert.equal((await ask('上海',travel)).provider,'company-policy-documents')
 console.log('PASS: short policy introduction, contextual follow-up, complete source retention, explicit full text, greetings and travel clarification; no business writes')
 const pw=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));const browser=await pw.chromium.launch({channel:'msedge',headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000}});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message))
 try {
  await page.goto(base+'/agent/');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('link',{name:'前往工作台 ↗'}).waitFor();assert.equal(await page.locator('.assistant-navigation').getByText('我能办理什么',{exact:true}).count(),0);assert.equal(await page.locator('.assistant-navigation').getByText('知识库 · 政策图谱',{exact:true}).count(),0)
  async function say(text){const r=page.waitForResponse(r=>r.url().endsWith('/api/web-chat/messages'));await page.getByPlaceholder('给 HRAgent 发送消息').fill(text);await page.getByRole('button',{name:'发送',exact:true}).click();assert.equal((await r).status(),200)}
  await say('我想了解年假');await page.getByRole('button',{name:'使用期限',exact:true}).click();await page.locator('.policy-details').waitFor();assert.equal(await page.locator('.policy-details').getAttribute('open'),null);await page.getByText('查看完整依据与适用说明',{exact:true}).click();await page.locator('.policy-details .markdown-content').getByText(/PDF第3页/).waitFor();await page.getByText('查看完整依据与适用说明',{exact:true}).click()
  fs.mkdirSync('.artifacts/conversation-tone',{recursive:true});await page.screenshot({path:'.artifacts/conversation-tone/assistant.png',fullPage:true});assert.deepEqual(errors,[])
  console.log('PASS: simplified sidebar, natural policy choices, collapsed evidence and source expansion')
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
