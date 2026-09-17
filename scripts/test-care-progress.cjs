// Read-only dialogue smoke check; no employee applications, reviews or messages to HR.
const assert=require('node:assert/strict');
(async()=>{
 const base='http://localhost:5173/api';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});assert.equal(login.status,200);const s=(await login.json()).data;
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
 const read=async path=>{const r=await fetch(base+path,{headers});assert.equal(r.status,200);return (await r.json()).data};
 const cases=await read('/employee-relations/cases');assert.ok(!cases.some(c=>['URGENT','HANDOFF'].includes(c.riskLevel||c.category||c.type)&&!['CLOSED','RESOLVED','COMPLETED'].includes(c.status)),'Do not test during active human support');
 const before=await read('/lifecycle/mine');const cid='care-progress-'+Date.now();
 const ask=async message=>{const r=await fetch(base+'/web-chat/messages',{method:'POST',headers,body:JSON.stringify({message,conversationId:cid})});assert.equal(r.status,200);return (await r.json()).data};
 let r=await ask('工作太多');assert.equal(r.provider,'workplace-support');r=await ask('做平台');assert.match(r.answer,/小版本/);assert.match(r.answer,/辛苦啦/);assert.doesNotMatch(r.answer,/最大的影响/);
 r=await ask('明天就要交了');assert.match(r.answer,/交付范围/);r=await ask('请假批了吗');assert.equal(r.provider,'assistant-workspace');assert.doesNotMatch(r.answer,/页面上方|每 5 秒/);
 assert.deepEqual(await read('/lifecycle/mine'),before);console.log('PASS: live care follow-up gives next steps; progress query reads employee records; no service applications created');
})().catch(e=>{console.error(e);process.exitCode=1});
