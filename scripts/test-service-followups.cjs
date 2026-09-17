// Guidance and status reads only. Never clicks start actions or submits employee applications.
const assert=require('node:assert/strict');
(async()=>{
 const base='http://localhost:5173/api';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});assert.equal(login.status,200);const s=(await login.json()).data;
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
 const read=async path=>{const r=await fetch(base+path,{headers});assert.equal(r.status,200);return(await r.json()).data};
 const ask=async(message,cid)=>{const r=await fetch(base+'/web-chat/messages',{method:'POST',headers,body:JSON.stringify({message,conversationId:cid})});assert.equal(r.status,200,message);return(await r.json()).data};
 const before=await read('/lifecycle/mine'),leaves=await read('/leave/my'),prefix='service-followups-'+Date.now();
 const scenarios=[['年假','我要申请年假'],['病假','我要申请病假'],['产假','我要申请产假'],['在职证明','我要开在职证明'],['收入证明','我要开收入证明'],['社保公积金','我要咨询社保公积金'],['合同续签','我要咨询合同签署'],['入职材料','我要办理入职材料核验'],['人事变更','我要办理人事变更'],['报销','我要办理报销协助'],['出差','我要办理出差协助'],['借款','我要办理借款协助'],['加班调休','我要办理加班调休协助'],['福利','我要办理福利协助']];
 for(let i=0;i<scenarios.length;i++){
  const [topic,command]=scenarios[i],cid=prefix+'-'+i;await ask('我想了解'+topic,cid);
  let r=await ask('我应该怎么申请',cid);assert.ok(r.actions.some(a=>a.value===command),topic+' did not offer '+command);assert.ok(r.answer.length<260,topic+' response too long');assert.ok(!r.details);assert.doesNotMatch(r.answer,/再说一点具体情况|没能准确理解/);
  r=await ask('下一步呢',cid);assert.ok(r.actions.some(a=>a.value===command),topic+' lost next-step context');
  if(['年假','收入证明','产假'].includes(topic)){r=await ask('查进度',cid);assert.equal(r.provider,topic==='年假'?'assistant-workspace':'hrssc-lifecycle',topic+' queried wrong business');}
 }
 let r=await ask('我应该怎么申请',prefix+'-new');assert.ok(r.actions.some(a=>a.value==='我要申请请假'));assert.ok(r.actions.some(a=>a.value==='我要开在职证明'));
 await ask('我想了解年假',prefix+'-switch');r=await ask('社保怎么申请',prefix+'-switch');assert.ok(r.actions.some(a=>a.value==='我要咨询社保公积金'));assert.ok(!r.actions.some(a=>a.value==='我要申请年假'));
 assert.deepEqual(await read('/lifecycle/mine'),before);assert.deepEqual(await read('/leave/my'),leaves);
 console.log('PASS: 14 service topics, contextual application and next steps, correct status channel, unknown-service clarification, explicit topic switch; no applications created');
})().catch(e=>{console.error(e);process.exitCode=1});
