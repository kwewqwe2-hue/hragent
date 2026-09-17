// Read-only checks and policy questions; no applications, reviews or company benefit assertions are created.
const assert=require('node:assert/strict'),fs=require('fs'),crypto=require('crypto');
(async()=>{
 const base='http://localhost:5173/api';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});assert.equal(login.status,200);const s=(await login.json()).data;
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
 async function call(path,body){const r=await fetch(base+path,{headers,method:body?'POST':'GET',...(body?{body:JSON.stringify(body)}:{})});assert.equal(r.status,200,path);const envelope=await r.json();assert.equal(envelope.success,true);return envelope.data;}
 const before=await call('/lifecycle/mine');
 const status=await call('/policy-monitor/progress');assert.equal(status.sources.length,5);assert.ok(status.sources.some(s=>s.id==='finance'));assert.ok(status.progress.sourcesFinished<=status.sources.length);assert.ok(Number.isInteger(status.pendingCount));assert.ok(!('candidates' in status));
 const denied=await fetch(base+'/admin/policy-monitor/scan',{method:'POST',headers});assert.equal(denied.status,403);
 const unauth=await fetch(base+'/policy-monitor/progress');assert.ok([401,403].includes(unauth.status));
 const chat=await call('/web-chat/messages',{message:'我们公司有企业年金吗',conversationId:'knowledge-readonly-'+Date.now()});assert.equal(chat.provider,'company-benefit-guidance');assert.match(chat.answer,/不能直接说有或没有/);assert.ok(chat.answer.length<300);
 const policy=await call('/employee-services/policy/ask',{message:'我们公司有企业年金吗'});assert.match(policy.answer,/不能直接说有或没有/);assert.doesNotMatch(policy.answer,/暂未设立/);
 assert.deepEqual(await call('/lifecycle/mine'),before);
 const html=fs.readFileSync('hragentv1/frontend/dist/index.html','utf8');const asset=html.match(/src="([^"]+\.js)"/)[1];const remote=Buffer.from(await (await fetch('http://localhost:5173'+asset)).arrayBuffer());const local=fs.readFileSync('hragentv1/frontend/dist'+asset);assert.equal(crypto.createHash('sha256').update(remote).digest('hex'),crypto.createHash('sha256').update(local).digest('hex'));
 console.log(JSON.stringify({result:'PASS: progress access, scan permission, company-benefit answers in both channels, deployed asset match; no application changes',checked:status.progress.documentsChecked,pending:status.pendingCount,sourceErrors:status.checks.filter(c=>c.error).map(c=>c.id)},null,2));
})().catch(e=>{console.error(e);process.exitCode=1});
