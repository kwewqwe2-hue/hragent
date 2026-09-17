const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path')
const {chromium}=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'))
;(async()=>{
 const base='http://localhost:8080/api'
 const session=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data
 const headers={'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)}
 const journey=(await(await fetch(base+'/employee-relations/journey',{headers})).json()).data
 const original=journey.find(t=>t.key==='day1-account')
 const browser=await chromium.launch({headless:true,channel:'msedge'});let changed=false
 const output=path.resolve(__dirname,'../.artifacts/care-functions');await fs.mkdir(output,{recursive:true})
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1100}});const errors=[];page.on('pageerror',e=>errors.push(e.message))
  await page.goto('http://localhost:5173/login');await page.locator('input').nth(0).fill('zhangsan');await page.locator('input').nth(1).fill('123456');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>!u.pathname.includes('login'))
  await page.goto('http://localhost:5173/employee-services');await page.locator('.service-card').filter({hasText:'入职与成长导航'}).click();await page.waitForURL('**/employee-experience?section=onboarding')
  await page.getByRole('heading',{name:'入职与成长导航',exact:true}).waitFor();await page.locator('.er-task').first().waitFor()
  await page.getByLabel('查看阶段').selectOption('第一天');assert.equal(await page.locator('.er-task').count(),2)
  const task=page.locator('.er-task').filter({hasText:'确认账号与设备'});await task.getByText('展开行动指引',{exact:true}).click();assert.equal(await task.locator('li').count(),3)
  assert.equal(await task.getByRole('link',{name:'查看我的账号 →'}).getAttribute('href'),'http://localhost:5173/account')
  const checkbox=task.getByRole('checkbox');const save=page.waitForResponse(r=>r.url().endsWith('/journey/day1-account')&&r.request().method()==='PUT')
  changed=true;await checkbox.click();assert.ok((await save).ok());await page.waitForFunction(expected=>document.querySelector('.er-task input').checked===expected,!original.done)
  await page.reload();await page.locator('.er-task').first().waitFor();assert.equal(await page.locator('.er-task').filter({hasText:'确认账号与设备'}).getByRole('checkbox').isChecked(),!original.done)
  await page.getByLabel('想理解的话').fill('先对齐目标，再把问题闭环');await page.getByRole('button',{name:'解释这句话',exact:true}).click()
  assert.equal(await page.locator('.wt-result').first().locator(':scope > p').first().innerText(),'先确认目标一致，再跟进问题直到解决，并反馈结果')
  await page.getByLabel('系统或工具').fill('新人项目库');await page.getByLabel('工作用途',{exact:true}).fill('查阅项目需求');await page.getByLabel('使用期限').fill('项目结束前');await page.getByRole('button',{name:'生成申请说明',exact:true}).click()
  const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'下载申请说明',exact:true}).click();const download=await downloadPromise;await download.saveAs(path.join(output,'permission.txt'));assert.match(await fs.readFile(path.join(output,'permission.txt'),'utf8'),/新人项目库/)
  await page.getByRole('heading',{name:'把职场用语说清楚',exact:true}).scrollIntoViewIfNeeded();await page.screenshot({path:path.join(output,'onboarding-tools.png')})
  await page.goto('http://localhost:5173/employee-services');await page.locator('.service-card').filter({hasText:'支持与关怀咨询'}).click();await page.waitForURL('**/employee-experience?section=support')
  await page.getByRole('heading',{name:'支持与关怀咨询',exact:true}).waitFor();assert.equal(await page.locator('.er-task').count(),0);assert.equal(await page.getByRole('heading',{name:'把职场用语说清楚'}).count(),0)
  await page.getByLabel('现在最困扰你的事').fill('三个任务都要今天交付');await page.getByLabel('你希望先改变什么？').fill('确认先交付哪一项');await page.getByRole('button',{name:'整理下一步',exact:true}).click();assert.match(await page.locator('.cw-plan').innerText(),/三个任务都要今天交付/);assert.match(await page.locator('.cw-plan').innerText(),/确认先交付哪一项/)
  await page.getByLabel('想咨询的问题').fill('你好');const consult=page.waitForResponse(r=>r.url().endsWith('/web-chat/messages')&&r.request().method()==='POST');await page.getByRole('button',{name:'发送咨询',exact:true}).click();assert.ok((await consult).ok());await page.locator('.cw-answer').waitFor();assert.match(await page.locator('.cw-answer').innerText(),/你好/)
  await page.getByRole('heading',{name:'支持与关怀咨询',exact:true}).scrollIntoViewIfNeeded();await page.screenshot({path:path.join(output,'support.png')})
  await page.getByRole('button',{name:'清空本页记录',exact:true}).click();assert.equal(await page.locator('.cw-plan').count(),0);assert.equal(await page.locator('.cw-answer').count(),0)
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'support-mobile.png'),fullPage:true});assert.deepEqual(errors,[])
  console.log('PASS: distinct entry points, expanded steps, phase filter, server-persisted progress, contextual term replacement, permission download, personalized care plan, live assistant consultation, clear records, no JS errors')
 }finally{
  if(changed){const r=await fetch(base+'/employee-relations/journey/day1-account',{method:'PUT',headers,body:JSON.stringify({enabled:original.done})});assert.ok(r.ok,'Restore original test progress')}
  await browser.close()
 }
})().catch(e=>{console.error(e);process.exitCode=1})
