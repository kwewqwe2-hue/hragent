// UI mutations are intercepted. No employee application or medical document is stored by this test.
const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
(async()=>{
 const base='http://localhost:5173',pw=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));
 const login=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});const session=(await login.json()).data;
 const headers={Authorization:'Bearer '+session.token,'X-Workspace-Id':String(session.user.tenantId)};
 const read=async p=>{const r=await fetch(base+'/api'+p,{headers});assert.equal(r.status,200);return (await r.json()).data};
 const before=await read('/lifecycle/mine'),beforeLeaves=await read('/leave/my');
 const browser=await pw.chromium.launch({channel:'msedge',headless:true});const page=await browser.newPage({viewport:{width:1366,height:940}});page.setDefaultTimeout(30000);
 let phase=0,scans=0,submits=0,status='PENDING_HR',polls=0;const errors=[];page.on('pageerror',e=>errors.push(e.message));
 fs.mkdirSync('.artifacts/leave-medical',{recursive:true});
 try{
  // Generate a synthetic document only for local OCR smoke checks. Never submit it to the employee API.
  const fixture=await browser.newPage({viewport:{width:900,height:650}});await fixture.setContent('<html><meta charset="utf-8"><body style="font:28px Microsoft YaHei;background:white;padding:40px;line-height:1.8"><h2>某某医院 · 门诊病历</h2><p>姓名：测试员工　性别：男</p><p>就诊日期：2026-09-08</p><p>诊断：合成测试材料</p><p>医嘱：此文件仅用于软件测试，无医疗效力</p></body></html>');await fixture.screenshot({path:'.artifacts/leave-medical/synthetic-record.png'});await fixture.pdf({path:'.artifacts/leave-medical/synthetic-record.pdf',printBackground:true});await fixture.close();
  await page.route('**/api/leave/my',route=>{polls++;return route.fulfill({json:{success:true,data:[{id:900001,status,statusLabel:status==='PENDING_HR'?'待 HR 备案':'已通过',startDate:'2026-09-09',endDate:'2026-09-10',hrOpinion:status==='APPROVED'?'审核通过（界面测试）':null,hrRecordedAt:status==='APPROVED'?'2026-09-09T15:00:00':null}]}})});
  await page.route('**/api/web-chat/messages',async route=>{const {message}=route.request().postDataJSON();let answer,actions=[];
   if(message==='我想休病假'){phase=1;answer='身体不舒服，先照顾好自己。我们一步一步填写，从哪天开始？'}
   else if(phase===1){phase=2;answer='到哪天结束？'}else if(phase===2){phase=3;answer='请简要说明请假原因。'}else if(phase===3){phase=4;answer='请核对这份假期申请。还差一份病历或诊断证明，初检后再确认提交。';actions=[{label:'上传病历并初检',type:'medical-upload',value:'900001'}]}
   else if(message==='确认提交'){assert.equal(scans,2);submits++;answer='已提交，等待主管与 HR 审核。';actions=[{label:'查看我的服务单',type:'requests',value:''}]}
   else throw Error('Unexpected chat step '+message);
   await route.fulfill({json:{success:true,data:{answer,actions,provider:'hrssc-lifecycle',requestId:'fixture'}}});
  });
  await page.route('**/api/lifecycle/900001/medical-record',async route=>{scans++;assert.match(route.request().headers()['content-type'],/multipart/);
   if(scans===1)return route.fulfill({status:400,json:{success:false,message:'病历上的患者姓名与员工档案暂时没有匹配上。请核对材料。'}});
   return route.fulfill({json:{success:true,data:{answer:'请核对这份假期申请：病假材料已初检，真实性和医嘱由人工核验。回复确认提交。',actions:[{label:'确认提交申请',type:'message',value:'确认提交'}],provider:'hrssc-lifecycle',requestId:'fixture'}}});
  });
  await page.goto(base+'/agent/');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('link',{name:'前往工作台 ↗'}).waitFor();
  const say=async text=>{const response=page.waitForResponse(r=>r.url().endsWith('/api/web-chat/messages'));await page.getByPlaceholder('给 HRAgent 发送消息').fill(text);await page.getByRole('button',{name:'发送',exact:true}).click();assert.equal((await response).status(),200)};
  await say('我想休病假');await say('2026-09-09');await say('2026-09-10');await say('身体不适');
  const input=page.getByLabel('上传病历并初检', {exact:true});await input.waitFor();assert.equal(await page.getByRole('button',{name:'确认提交申请',exact:true}).count(),0);
  await input.setInputFiles('.artifacts/leave-medical/synthetic-record.png');await page.getByRole('alert').filter({hasText:'姓名'}).waitFor();assert.equal(submits,0);
  await input.setInputFiles('.artifacts/leave-medical/synthetic-record.png');await page.getByRole('button',{name:'确认提交申请',exact:true}).waitFor();assert.equal(submits,0);
  await page.screenshot({path:'.artifacts/leave-medical/medical-review.png',fullPage:true});
  await page.getByRole('button',{name:'确认提交申请',exact:true}).click();await page.getByText('已提交，等待主管与 HR 审核。',{exact:true}).waitFor();assert.equal(submits,1);
  status='APPROVED';await page.getByRole('status').filter({hasText:'900001'}).waitFor({timeout:12000});assert.ok(polls>=2);assert.equal(await page.evaluate(()=>document.querySelector('.messages').getBoundingClientRect().top >= document.querySelector('.approval-updates').getBoundingClientRect().bottom-1),true);
  await page.getByRole('button',{name:'查看结果',exact:true}).click();await page.getByText('HR：审核通过（界面测试）',{exact:true}).waitFor();await page.screenshot({path:'.artifacts/leave-medical/approval-updated.png',fullPage:true});
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await page.screenshot({path:'.artifacts/leave-medical/mobile.png',fullPage:true});
  assert.deepEqual(errors,[]);assert.deepEqual(await read('/lifecycle/mine'),before);assert.deepEqual(await read('/leave/my'),beforeLeaves);
  console.log('PASS: sick-leave form, rejected scan/retry, explicit confirmation, approval polling and opinion, mobile; no actual business writes');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
