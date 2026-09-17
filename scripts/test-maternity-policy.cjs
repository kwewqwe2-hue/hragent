const assert=require('node:assert/strict'),path=require('node:path')
const {chromium}=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'))
;(async()=>{
 const base='http://localhost:8080/api';let ready=false
 for(let i=0;i<40;i++){try{if((await fetch(base+'/health',{signal:AbortSignal.timeout(3000)})).ok){ready=true;break}}catch{};await new Promise(r=>setTimeout(r,3000))}
 assert.ok(ready)
 const session=(await (await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data
 async function ask(message){const p=await(await fetch(base+'/web-chat/messages',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)},body:JSON.stringify({message})})).json();assert.equal(p.success,true);return p.data}
 for(const question of ['我想问问产假问题','产假多少天','北京产假多少天']) {
  const {answer}=await ask(question);assert.match(answer,/98/);assert.match(answer,/咨询 HR/);assert.doesNotMatch(answer,/缴费|hrmanual|2022年版|手册|暂时没有/)
  if(question.startsWith('北京'))assert.doesNotMatch(answer,/158/)
  console.log(JSON.stringify({question,answer}))
 }
 const benefit=await ask('产假工资怎么发');assert.match(benefit.answer,/生育津贴/);assert.doesNotMatch(benefit.answer,/基础产假/)
 assert.equal((await ask('我还有多少年假')).provider,'personal-business')
 const browser=await chromium.launch({headless:true,channel:'msedge'})
 try{const page=await browser.newPage({viewport:{width:1440,height:1000}});await page.goto('http://localhost:5174/');await page.getByRole('button',{name:'登录',exact:true}).click();await page.locator('.composer textarea').fill('我想问问产假问题');await page.getByRole('button',{name:'发送',exact:true}).click();await page.locator('.message-row.assistant .markdown-content').filter({hasText:'98'}).waitFor();await page.screenshot({path:path.resolve(__dirname,'../.artifacts/maternity-policy.png')});console.log('PASS: maternity days, regional boundary, benefits, personal balance regression, original chat')}
 finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1})
