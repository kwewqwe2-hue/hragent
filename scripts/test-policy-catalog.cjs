const assert=require('node:assert/strict')
;(async()=>{
 const base='http://localhost:8080/api';let ready=false
 for(let i=0;i<40;i++){try{if((await fetch(base+'/health',{signal:AbortSignal.timeout(3000)})).ok){ready=true;break}}catch{};await new Promise(r=>setTimeout(r,3000))}
 assert.ok(ready,'Backend readiness')
 const session=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data
 async function ask(message){const r=await fetch(base+'/web-chat/messages',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)},body:JSON.stringify({message})});const p=await r.json();assert.equal(p.success,true,JSON.stringify(p));return p.data}
 for(const q of ['你有哪些政策知识','介绍一下知识库','有哪些政策种类','能问什么']){
  const a=await ask(q);assert.equal(a.provider,'policy-catalog');assert.match(a.answer,/休假与考勤/);assert.match(a.answer,/你想先了解哪一类/);assert.ok(a.answer.length<500);console.log('PASS catalog: '+q)
 }
 assert.equal((await ask('我还有多少年假')).provider,'personal-business')
 assert.notEqual((await ask('产假有哪些政策')).provider,'policy-catalog')
 assert.notEqual((await ask('休假与考勤有哪些政策')).provider,'policy-catalog')
 console.log('PASS personal balance and specific follow-up questions preserved')
})().catch(e=>{console.error(e);process.exitCode=1})
