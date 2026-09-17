// Read-only live conversation checks. Never creates, submits, cancels or approves a business request.
const assert=require('node:assert/strict');
const root='http://localhost:5173/api';
(async()=>{
 let ready=false;for(let i=0;i<25;i++){try{if((await fetch(root+'/health',{signal:AbortSignal.timeout(2000)})).ok){ready=true;break}}catch{}await new Promise(r=>setTimeout(r,1000))}assert(ready,'backend readiness');
 const login=await(await fetch(root+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json();assert(login.success);
 const headers={Authorization:'Bearer '+login.data.token,'X-Workspace-Id':String(login.data.user.tenantId),'Content-Type':'application/json'};
 async function get(path){const r=await fetch(root+path,{headers});assert(r.ok);return (await r.json()).data}
 async function say(message,conversationId){const r=await fetch(root+'/web-chat/messages',{method:'POST',headers,body:JSON.stringify({message,conversationId}),signal:AbortSignal.timeout(20000)});assert.equal(r.status,200,message);const result=(await r.json()).data;assert(result?.answer,message);return result}
 try{
  const before=await get('/lifecycle/mine');const cid='routing-readonly-'+Date.now();
  let r=await say('我想请年假，还想开在职证明',cid);assert.equal(r.provider,'intent-guidance');assert.equal(r.actions.length,2);console.log('PASS multiple service clarification');
  r=await say('帮我打开在职证明',cid);assert.equal(r.provider,'assistant-workspace');assert(r.actions.some(a=>a.value==='/certificates'));console.log('PASS view certificate without new application');
  r=await say('办好了没呀',cid);assert(r.answer.includes('服务')||r.answer.includes('申请'));console.log('PASS contextual progress lookup');
  r=await say('我不要开在职证明',cid);assert(r.answer.includes('先不发起申请'));console.log('PASS negated application');
  r=await say('我的年假还剩几天呀',cid);assert.equal(r.provider,'assistant-workspace');console.log('PASS live annual balance');
  r=await say('帮我查一下2026年8月工资条',cid);assert(r.answer.includes('2026-08')||r.answer.includes('2026年8月'));console.log('PASS payroll month retained');
  r=await say('我的年假总共多少天',cid+'-policy');assert(r.answer.includes('累计工作多久'));
  r=await say('三年',cid+'-policy');assert(r.answer.includes('12个月'));
  r=await say('已经连续工作满12个月',cid+'-policy');assert(r.answer.includes('5 天'));
  r=await say('接下来怎么申请',cid+'-policy');assert(r.actions.some(a=>a.value==='我要申请年假'));console.log('PASS policy interview to application guidance');
  const after=await get('/lifecycle/mine');assert.deepEqual(after,before);console.log('PASS no real requests created or changed');
 }finally{await fetch(root+'/auth/logout',{method:'POST',headers})}
})().catch(e=>{console.error(e.message);process.exitCode=1});
