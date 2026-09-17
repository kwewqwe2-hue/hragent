const fs=require('node:fs'),assert=require('node:assert/strict');
(async()=>{
 const dir='.artifacts/hrmanual-cleanup/',read=f=>JSON.parse(fs.readFileSync(dir+f,'utf8'));
 const before=read('before.json'),plan=read('plan.json'),state=read('applied.json');
 const base='http://localhost:5173/api';
 async function library(username){const login=await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})})).json();assert.ok(login.success);const s=login.data;const r=await(await fetch(base+'/admin/knowledge',{headers:{Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId)}})).json();assert.ok(r.success);return r.data;}
 const all=await library('wanghr'),employee=await library('zhangsan');
 assert.equal(state.deleted.length,865);assert.equal(state.created.length,137);
 assert.ok(!all.some(a=>plan.deleteIds.includes(a.id)));
 for(const a of before.filter(a=>!plan.deleteIds.includes(a.id)))assert.deepEqual(all.find(b=>b.id===a.id),a,'Unrelated record changed '+a.id);
 for(const item of [...plan.retained,...plan.official]){const id=state.created.find(x=>x.key===item.key).id,a=employee.find(x=>x.id===id);assert.ok(a,item.key);for(const [key,value] of Object.entries(item.record))assert.equal(a[key],value,item.key+' '+key);}
 assert.equal(plan.retained.length,131);assert.ok(plan.retained.every(x=>x.record.reviewStatus==='REFERENCE'&&!x.record.effectiveFrom));assert.equal(plan.official.length,6);
 for(const [url,path] of [['http://localhost:5173/agent/','hragent-chat/dist/index.html'],['http://localhost:5173/knowledge','hragentv1/frontend/dist/index.html']]){const live=await(await fetch(url)).text(),built=fs.readFileSync(path,'utf8');const asset=built.match(/src="([^"]+\.js)"/)[1];assert.ok(live.includes(asset),url+' must serve new build');}
 console.log('PASS: 865 old rows removed, 131 classified references + 6 official updates visible, unrelated records unchanged, both updated UI builds deployed.');
})().catch(e=>{console.error(e);process.exitCode=1});
