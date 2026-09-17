const fs=require('node:fs'),assert=require('node:assert/strict');
(async()=>{
 const base='http://localhost:5173/api';
 const session=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'}),signal:AbortSignal.timeout(15000)})).json()).data;
 assert.ok(session?.token);
 const headers={Authorization:'Bearer '+session.token,'X-Workspace-Id':String(session.user.tenantId)};
 async function api(path,options={}){const r=await fetch(base+path,{...options,headers:{...headers,...options.headers},signal:AbortSignal.timeout(20000)});return {status:r.status,body:await r.json()};}
 const name='在职证明-自备模板-演示示例.docx';
 const form=new FormData();form.append('file',new Blob([fs.readFileSync('docs/演示材料/'+name)],{type:'application/vnd.openxmlformats-officedocument.wordprocessingml.document'}),name);
 const preview=await api('/employment-certificate-templates/preview',{method:'POST',body:form});
 assert.equal(preview.status,200);assert.equal(preview.body.data.canUpload,true);
 const before=await api('/lifecycle/mine');assert.equal(before.status,200);
 const invalid=new FormData();invalid.append('file',new Blob(['invalid docx regression fixture']),'template.docx');invalid.append('message','帮我办一下在职证明');invalid.append('conversationId','template-fix-invalid-'+Date.now());
 const start=performance.now();const rejected=await api('/web-chat/attachments',{method:'POST',body:invalid});const duration=Math.round(performance.now()-start);
 assert.equal(rejected.status,400);assert.match(rejected.body.message,/无法读取/);assert.ok(duration<15000);
 const after=await api('/lifecycle/mine');assert.deepEqual(after.body.data,before.body.data,'Invalid upload must not create an application');
 const result={fixture:name,recognizedFields:preview.body.data.placeholders.length,invalidTemplateResponseMs:duration,applicationRecordsUnchanged:true};
 fs.writeFileSync('.artifacts/template-attachment-fix/live-check.json',JSON.stringify(result,null,2));
 console.log('PASS: actual demo DOCX parsed; invalid DOCX rejected locally in '+duration+' ms; no application or approval created.');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
