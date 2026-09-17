const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs/promises');
(async()=>{
 const base='http://localhost:5174/api';
 const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});const s=(await login.json()).data;assert.ok(s.token);
 const headers={'Content-Type':'application/json',Authorization:`Bearer ${s.token}`,'X-Workspace-Id':String(s.user.tenantId)};
 const mine=async()=>(await(await fetch(base+'/lifecycle/mine',{headers})).json()).data.map(r=>r.id);
 const before=await mine();
 for(const message of ['有哪些全周期服务','入职','在职','离职','离职后']){const r=await fetch(base+'/web-chat/messages',{method:'POST',headers,body:JSON.stringify({message,conversationId:'navigation-readonly'})});assert.equal(r.status,200);const data=(await r.json()).data;assert.equal(data.provider,'hrssc-lifecycle');assert.ok(data.actions.length>0);assert.ok(!data.answer.includes('暂时还答不上来'));}
 const pw=require(path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'));const browser=await pw.chromium.launch({channel:'msedge',headless:true});const page=await browser.newPage({viewport:{width:1366,height:900}});page.setDefaultTimeout(45000);const errors=[];page.on('pageerror',e=>errors.push(e.message));await fs.mkdir('.artifacts/lifecycle-guidance',{recursive:true});let submitted=0;const conversationIds=new Set();
 try{
  await page.goto('http://localhost:5174/');await page.getByRole('button',{name:'登录',exact:true}).click();await page.getByRole('button',{name:'新对话',exact:true}).first().waitFor();await page.getByRole('button',{name:'新对话',exact:true}).first().click();
  const say=async text=>{const reply=page.waitForResponse(r=>r.url().endsWith('/api/web-chat/messages')&&r.request().method()==='POST');await page.getByPlaceholder('给 HRAgent 发送消息').fill(text);await page.getByRole('button',{name:'发送',exact:true}).click();assert.ok((await reply).ok());await page.getByRole('button',{name:'发送',exact:true}).waitFor();};
  await say('有哪些全周期服务');await page.getByRole('button',{name:'在职服务',exact:true}).click();await page.getByRole('button',{name:'对话申请请假',exact:true}).waitFor();await page.getByRole('button',{name:'打开请假填写',exact:true}).click();assert.ok((await page.locator('.assistant-panel iframe').getAttribute('src')).includes('/my-leave'));await page.getByRole('button',{name:'关闭办理面板'}).click();await page.screenshot({path:'.artifacts/lifecycle-guidance/stage.png',fullPage:true});
  // All form writes are intercepted; server state transitions are covered by LifecycleServiceTest.
  const messageAction=(label,value=label)=>({label,type:'message',value});const cancel=messageAction('取消办理');
  await page.route('**/api/web-chat/messages',async route=>{const input=route.request().postDataJSON();conversationIds.add(input.conversationId);let answer,actions=[cancel];switch(input.message){
   case '我要申请请假':answer='开始填写假期申请。第 1 / 4 步：你要请哪种假？';actions=[messageAction('事假'),cancel];break;
   case '事假':answer='第 2 / 4 步：从哪天开始？';break;
   case '2026-10-12':case '2026-10-14':answer='第 3 / 4 步：到哪天结束？';break;
   case '2026-10-13':case '2026-10-15':answer='第 4 / 4 步：请简要说明请假原因。';break;
   case '个人安排':answer='请核对这份假期申请：以上是待提交草稿。';actions=[messageAction('确认提交申请','确认提交'),messageAction('修改开始日期'),cancel];break;
   case '修改开始日期':answer='已回到该步骤。第 2 / 4 步：从哪天开始？';break;
   case '确认提交':submitted++;answer='已提交，服务单 #TEST。请假进入现有主管审批及 HR 备案流程。';actions=[{label:'查看我的服务单',type:'requests',value:''}];break;
   default:throw Error('Unexpected form message '+input.message);
  }await route.fulfill({json:{success:true,data:{answer,actions,provider:'hrssc-lifecycle',requestId:'ui-fixture'}}});});
  await page.getByRole('button',{name:'对话申请请假',exact:true}).click();await page.getByRole('button',{name:'事假',exact:true}).click();await page.getByText('第 2 / 4 步：从哪天开始？',{exact:true}).waitFor();await say('2026-10-12');await say('2026-10-13');await say('个人安排');await page.getByRole('button',{name:'修改开始日期',exact:true}).click();await page.getByText('已回到该步骤。',{exact:false}).waitFor();assert.equal(await page.getByRole('button',{name:'确认提交申请',exact:true}).isDisabled(),true);await say('2026-10-14');await say('2026-10-15');await say('个人安排');assert.equal(submitted,0);
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);await page.screenshot({path:'.artifacts/lifecycle-guidance/mobile.png',fullPage:true});await page.getByRole('button',{name:'确认提交申请',exact:true}).last().click();await page.getByRole('button',{name:'查看我的服务单',exact:true}).last().click();await page.getByRole('heading',{name:'我的服务单',exact:true}).waitFor();assert.equal(submitted,1);assert.equal(conversationIds.size,1);assert.deepEqual(errors,[]);assert.deepEqual(await mine(),before);console.log('PASS: real 4-stage navigation, original 在职 follow-up, form choices/correction/confirmation UI (writes mocked), links, request view, mobile; no real application created');
 }catch(e){await page.screenshot({path:'.artifacts/lifecycle-guidance/failure.png',fullPage:true});console.log(await page.locator('.message-row').last().innerText());throw e}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});

