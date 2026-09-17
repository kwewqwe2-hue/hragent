// Read-only policy conversations. Never creates applications or changes employee profiles.
const assert=require('node:assert/strict');
(async()=>{
 const base='http://localhost:5173/api';
 const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});assert.equal(login.status,200);const s=(await login.json()).data;
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
 const read=async path=>{const r=await fetch(base+path,{headers});assert.equal(r.status,200);return (await r.json()).data};
 const before=await read('/lifecycle/mine'),leaves=await read('/leave/my');
 const ask=async(message,cid)=>{const r=await fetch(base+'/web-chat/messages',{method:'POST',headers,body:JSON.stringify({message,conversationId:cid})});assert.equal(r.status,200);return (await r.json()).data};
 const cid='maternity-dialogue-'+Date.now();let r=await ask('我想了解一下产假',cid);
 assert.match(r.answer,/哪个城市/);assert.doesNotMatch(r.answer,/HR|核对|158/);assert.ok(!r.details);
 r=await ask('上海',cid);assert.match(r.answer,/158天/);assert.match(r.answer,/98天/);assert.match(r.answer,/60天/);assert.doesNotMatch(r.answer,/哪个城市/);assert.ok(r.answer.indexOf('HR')>r.answer.indexOf('60天'));assert.ok(r.answer.length<230);assert.match(r.details,/shanghai.gov.cn/);
 r=await ask('难产呢',cid);assert.match(r.answer,/增加15天/);assert.doesNotMatch(r.answer,/哪个城市/);
 r=await ask('需要什么材料',cid);assert.match(r.answer,/计划休假/);assert.doesNotMatch(r.answer,/哪个城市/);
 r=await ask('我应该怎么申请',cid);assert.match(r.answer,/一步步|一项项/);assert.doesNotMatch(r.answer,/158天|哪个城市|具体情况吗/);assert.ok(!r.details);assert.ok(r.actions.some(a=>a.type==='message'&&a.value==='我要申请产假'));
 r=await ask('我应该怎么申请',cid+'-no-context');assert.ok(r.actions.some(a=>a.value==='我要申请请假'));assert.ok(!r.actions.some(a=>a.value==='我要申请产假'));
 r=await ask('我在上海，想了解产假',cid+'-direct');assert.match(r.answer,/158天/);assert.doesNotMatch(r.answer,/哪个城市/);
 r=await ask('北京产假多少天',cid+'-beijing');assert.match(r.answer,/98天/);assert.doesNotMatch(r.answer,/158天|60天/);
 assert.deepEqual(await read('/lifecycle/mine'),before);assert.deepEqual(await read('/leave/my'),leaves);
 console.log('PASS: maternity city follow-up, no early caveat, short sourced rules before applicability note, special cases and materials, regional isolation; no applications created');
})().catch(e=>{console.error(e);process.exitCode=1});
