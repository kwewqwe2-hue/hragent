// Expected validation failures only; never create a real application or store a medical record.
const assert=require('node:assert/strict');
(async()=>{
 const base='http://localhost:5173/api';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});const s=(await login.json()).data;
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId)};
 const read=async path=>{const r=await fetch(base+path,{headers});assert.equal(r.status,200);return (await r.json()).data};
 const before=await read('/leave/my'),drafts=await read('/lifecycle/mine');
 const form=new FormData();form.append('file',new Blob(['This is not a PDF or a medical record.'],{type:'application/pdf'}),'invalid.pdf');form.append('startDate','2026-09-09');form.append('endDate','2026-09-10');
 const badFile=await fetch(base+'/leave/medical-records',{method:'POST',headers,body:form});assert.equal(badFile.status,400);assert.match((await badFile.json()).message,/文件内容/);
 const missing=await fetch(base+'/leave',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({leaveType:'SICK',startDate:'2026-09-09',endDate:'2026-09-10',days:1,reason:'Validation must stop before creating a record'})});assert.equal(missing.status,400);assert.match((await missing.json()).message,/病历/);
 const progress=await fetch(base+'/web-chat/messages',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({message:'我的请假批了吗',conversationId:'leave-status-readonly'})});assert.equal(progress.status,200);const result=(await progress.json()).data;assert.equal(result.provider,'assistant-workspace');assert.ok(!result.details);
 assert.deepEqual(await read('/leave/my'),before);assert.deepEqual(await read('/lifecycle/mine'),drafts);
 console.log('PASS: multipart/date binding, invalid file rejection, sick-leave API gate, live approval query; no applications created');
})().catch(e=>{console.error(e);process.exitCode=1});
