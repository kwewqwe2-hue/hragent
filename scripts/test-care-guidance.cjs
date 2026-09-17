const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs')
;(async()=>{
 const base='http://localhost:5173';const response=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});const session=(await response.json()).data
 const headers={'Content-Type':'application/json',Authorization:'Bearer '+session.token,'X-Workspace-Id':String(session.user.tenantId)}
 const read=async p=>{const r=await fetch(base+'/api'+p,{headers});assert.equal(r.status,200);return (await r.json()).data}
 const beforeCases=await read('/employee-relations/cases'),beforeApplications=await read('/lifecycle/mine')
 assert.ok(!beforeCases.some(c=>['URGENT','HANDOFF'].includes(c.kind)&&c.status!=='RESOLVED'),'Do not run care checks with an active human-support case')
 const cid='care-readonly-'+Date.now()
 const ask=async(message,conversationId=cid)=>{const r=await fetch(base+'/api/web-chat/messages',{method:'POST',headers,body:JSON.stringify({message,conversationId})});assert.equal(r.status,200);return (await r.json()).data}
 for(const [q,pattern] of [['我现在心情很差',/休息/],['我想休息',/歇几分钟/],['先了解休假安排',/不用现在/],['担心主管不同意',/商量/],['他不答应',/HR/]]){const r=await ask(q);assert.equal(r.provider,'workplace-support');assert.match(r.answer,pattern);assert.ok(r.answer.length<350)}
 const obstacle=await ask('但是我没假期了');assert.equal(obstacle.provider,'workplace-support');assert.match(obstacle.answer,/无奈/);assert.doesNotMatch(obstacle.answer,/婚假|产假|条文|法规|PDF/);assert.ok(obstacle.answer.length<160);assert.match((await ask('那怎么办')).answer,/空档/);assert.match((await ask('我不想麻烦别人')).answer,/不一定要说很多/);assert.doesNotMatch((await ask('我想休息')).answer,/一段请假休息/);
 const hr=await ask('我想找HR协助沟通');assert.match(hr.answer,/没有替你发送消息或创建工单/);assert.ok(hr.actions.some(a=>a.type==='panel'))
 const neutral=await ask('领导故意针对我');assert.match(neutral.answer,/不能据此判断/)
 await ask('只想倾诉，不想听建议');const listen=await ask('昨天同事没和我说话，我一直惦记');assert.match(listen.answer,/我在听/);assert.equal(listen.actions.length,0)
 const support=await ask('我想了解心理支持渠道');assert.ok(support.actions.some(a=>a.value.includes('section=support')))
 // Crisis routing is tested with mocks only: never create real support tickets for testing.
 assert.deepEqual(await read('/employee-relations/cases'),beforeCases);assert.deepEqual(await read('/lifecycle/mine'),beforeApplications)
 console.log('PASS: emotional rest chain, neutral communication, listening, real support navigation; no cases or applications created')
 const pw=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));const browser=await pw.chromium.launch({channel:'msedge',headless:true});const context=await browser.newContext({viewport:{width:1440,height:1000}});const page=await context.newPage();page.setDefaultTimeout(30000);const errors=[];page.on('pageerror',e=>errors.push(e.message))
 try{
  await page.goto(base+'/agent/');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('link',{name:'前往工作台 ↗'}).waitFor()
  const say=async text=>{const r=page.waitForResponse(r=>r.url().endsWith('/api/web-chat/messages'));await page.getByPlaceholder('给 HRAgent 发送消息').fill(text);await page.getByRole('button',{name:'发送',exact:true}).click();assert.equal((await r).status(),200)}
  await say('我现在心情很差');await page.getByRole('button',{name:'我想休息',exact:true}).click();await page.getByRole('button',{name:'了解休假安排',exact:true}).click();await page.getByRole('button',{name:'聊聊怎么开口',exact:true}).click();await page.getByText(/不用一次把话说得很完美/).waitFor()
  fs.mkdirSync('.artifacts/care-guidance',{recursive:true});await page.screenshot({path:'.artifacts/care-guidance/desktop.png',fullPage:true})
  await page.getByRole('button',{name:'新对话',exact:true}).first().click();await say('先了解休假安排');await say('但是我没假期了');await page.getByText(/确实会有些无奈/).waitFor();assert.equal(await page.locator('.policy-details').count(),0);await page.screenshot({path:'.artifacts/care-guidance/gentle-obstacle.png',fullPage:true});
  await say('我想了解心理支持渠道');await page.getByRole('button',{name:'查看支持渠道',exact:true}).click();await page.frameLocator('.assistant-panel iframe').getByRole('heading',{name:'支持与关怀咨询',exact:true}).waitFor();await page.getByRole('button',{name:'关闭办理面板'}).click()
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);assert.deepEqual(errors,[])
  assert.deepEqual(await read('/employee-relations/cases'),beforeCases);assert.deepEqual(await read('/lifecycle/mine'),beforeApplications)
  console.log('PASS: conversation choices, rest-to-communication guidance, live EAP panel, mobile layout; no business submissions')
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
