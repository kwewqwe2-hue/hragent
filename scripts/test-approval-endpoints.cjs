// Read-only smoke checks of the exact routes consumed by the approval center.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),crypto=require('node:crypto');
const ts=require('../hragent-chat/node_modules/typescript'),context={exports:{}};vm.createContext(context);vm.runInContext(ts.transpileModule(fs.readFileSync('hragent-chat/src/approvals.ts','utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,context);
(async()=>{
 const base='http://localhost:5173';
 for(const username of ['zhangsan','lisi','wanghr']){
  const login=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})});assert.equal(login.status,200);const payload=(await login.json()).data,session={...payload,workspaceId:payload.user.tenantId};
  const sources=context.exports.approvalSources(session);let count=0;
  for(const source of sources){const r=await fetch(base+'/api'+source.path,{headers:{Authorization:'Bearer '+session.token,'X-Workspace-Id':String(session.workspaceId)}});assert.equal(r.status,200,session.user.role+' '+source.path);const body=await r.json();assert.equal(body.success,true);assert.ok(Array.isArray(body.data));const rows=context.exports.visibleApprovals(source,body.data,session);assert.ok(rows.every(row=>row.kind!=='lifecycle'||(!row.raw.leaveRequestId&&!row.raw.certificateRequestId)));if(source.side==='pending')assert.ok(rows.every(row=>row.employeeId!==(session.user.employeeProfileId??session.user.id)));count++;}
  console.log('PASS '+session.user.role+': '+count+' authorized approval sources read successfully');
 }
 for(const [folder,entry] of [['hragent-chat/dist','/agent/'],['hragentv1/frontend/dist','/index.html']]){
  const html=fs.readFileSync(folder+'/index.html','utf8'),path=html.match(/src="([^"]+\.js)"/)[1],local=fs.readFileSync(folder+'/assets/'+path.split('/').pop());const response=await fetch(base+path);assert.equal(response.status,200);const served=Buffer.from(await response.arrayBuffer());assert.equal(crypto.createHash('sha256').update(served).digest('hex'),crypto.createHash('sha256').update(local).digest('hex'));
  assert.equal((await fetch(base+entry)).status,200);
 }
 console.log('PASS: deployed assistant and workbench assets match tested builds; no approval or employee-data writes');
})().catch(e=>{console.error(e);process.exitCode=1});
