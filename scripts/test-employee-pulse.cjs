const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs/promises');
(async()=>{
 const base='http://localhost:8080/api';
 async function login(username){const r=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})});const s=(await r.json()).data;assert.ok(s.token);return {'Content-Type':'application/json',Authorization:`Bearer ${s.token}`,'X-Workspace-Id':String(s.user.tenantId)}}
 const employee=await login('zhangsan'),hr=await login('wanghr');
 const currentResponse=await fetch(base+'/employee-relations/pulse',{headers:employee});assert.equal(currentResponse.status,200);const current=(await currentResponse.json()).data;assert.equal(current.questions.length,5);
 assert.equal((await fetch(base+'/employee-relations/pulse/results?period='+current.period,{headers:employee})).status,403);
 const summary=await (await fetch(base+'/employee-relations/pulse/results?period='+current.period,{headers:hr})).json();assert.equal(summary.data.available,false);assert.equal(summary.data.participants,undefined);
 const denied=await fetch(base+'/employee-relations/pulse',{method:'POST',headers:employee,body:JSON.stringify({period:current.period,consent:false,answers:{}})});assert.equal(denied.status,400);
 const pw=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));
 const browser=await pw.chromium.launch({channel:'msedge',headless:true});await fs.mkdir('.artifacts/employee-pulse',{recursive:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});page.setDefaultTimeout(45000);const errors=[];page.on('pageerror',e=>errors.push(e.message));let submissions=0;
 try{
  await page.goto('http://localhost:5173/login');await page.locator('input').nth(0).fill('zhangsan');await page.locator('input').nth(1).fill('123456');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL(u=>!u.pathname.includes('login'));
  // Exercise rendering, validation and submit payload without adding test answers to a real account.
  await page.route('**/api/employee-relations/pulse',async route=>{if(route.request().method()==='POST'){const data=route.request().postDataJSON();assert.equal(data.consent,true);assert.equal(Object.keys(data.answers).length,5);submissions++;await route.fulfill({json:{success:true,data:'已保存'}})}else await route.fulfill({json:{success:true,data:{...current,submitted:false}}})});
  await page.goto('http://localhost:5173/employee-experience?section=support');const panel=page.locator('.pulse-panel');await panel.getByRole('heading',{name:'员工体验反馈'}).waitFor();const submit=panel.getByRole('button',{name:'提交体验反馈'});assert.equal(await submit.isDisabled(),true);
  await panel.getByRole('checkbox').check();await submit.click();assert.equal(submissions,0);await panel.getByText('请选择符合你体验的一项').first().waitFor();
  const radios=panel.getByRole('radio',{name:'比较认同',exact:true});assert.equal(await radios.count(),5);for(let i=0;i<5;i++){await panel.getByText('比较认同',{exact:true}).nth(i).click();assert.equal(await radios.nth(i).isChecked(),true);}
  await page.setViewportSize({width:390,height:844});await panel.scrollIntoViewIfNeeded();assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await page.screenshot({path:'.artifacts/employee-pulse/mobile.png',fullPage:true});await submit.click();await panel.getByText('感谢你的分享',{exact:false}).waitFor();assert.equal(submissions,1);
  const hrPage=await browser.newPage();await hrPage.goto('http://localhost:5173/login');await hrPage.locator('input').nth(0).fill('wanghr');await hrPage.locator('input').nth(1).fill('123456');await hrPage.getByRole('button',{name:'登录',exact:true}).click();await hrPage.waitForURL(u=>!u.pathname.includes('login'));await hrPage.goto('http://localhost:5173/employee-experience?section=support');await hrPage.getByRole('button',{name:'组织运营',exact:true}).click();await hrPage.getByRole('button',{name:'员工反馈',exact:true}).click();await hrPage.locator('.pulse-panel').getByText('参与人数未达到 5 人',{exact:false}).waitFor();await hrPage.screenshot({path:'.artifacts/employee-pulse/hr.png',fullPage:true});assert.deepEqual(errors,[]);console.log('PASS: live API permissions/consent, SurveyJS validation and submission (mocked write), mobile layout, HR threshold view; no real feedback written');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});

