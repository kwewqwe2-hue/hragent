// Explicit user-authorized cleanup of hrmanual.pdf rows. Retain an external audit/restore snapshot.
const fs=require('node:fs'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root='.artifacts/hrmanual-cleanup/',hash=x=>crypto.createHash('sha256').update(JSON.stringify(x)).digest('hex');
(async()=>{
 const plan=JSON.parse(fs.readFileSync(root+'plan.json','utf8')),before=JSON.parse(fs.readFileSync(root+'before.json','utf8'));
 assert.equal(plan.deleteIds.length,865);assert.equal(new Set(plan.deleteIds).size,865);
 for(const id of plan.deleteIds)assert.ok(before.find(a=>a.id===id)?.source.startsWith('hrmanual.pdf · '));
 const base='http://localhost:5173/api',e=await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'wanghr',password:'123456'})})).json();const s=e.data;assert.equal(s.user.role,'HR');assert.equal(s.user.tenantId,1);
 const headers={'Content-Type':'application/json',Authorization:'Bearer '+s.token,'X-Workspace-Id':'1'};
 async function call(path,method='GET',body){const r=await fetch(base+path,{headers,method,...(body?{body:JSON.stringify(body)}:{})});const e=await r.json();assert.ok(r.ok&&e.success,`${method} ${path}: ${e.message||r.status}`);return e.data;}
 let current=await call('/admin/knowledge');
 const state=fs.existsSync(root+'applied.json')?JSON.parse(fs.readFileSync(root+'applied.json','utf8')):{created:[],deleted:[]};
 const save=()=>fs.writeFileSync(root+'applied.json',JSON.stringify(state,null,2));
 for(const id of plan.deleteIds.filter(x=>!state.deleted.includes(x))){assert.equal(hash(current.find(a=>a.id===id)),hash(before.find(a=>a.id===id)),'Source changed since review: '+id);}
 if(!process.argv.includes('--apply')){console.log(JSON.stringify({create:plan.retained.length+plan.official.length,delete:plan.deleteIds.length,scope:'hrmanual.pdf only',otherRecords:'unchanged'}));return;}
 for(const item of [...plan.official,...plan.retained]) {
  if(state.created.some(x=>x.key===item.key))continue;
  let existing=current.find(a=>a.title===item.record.title && a.source===item.record.source && a.content===item.record.content);
  const a=existing||await call('/admin/knowledge','POST',item.record);state.created.push({key:item.key,id:a.id});save();
 }
 current=await call('/admin/knowledge');
 for(const item of [...plan.official,...plan.retained]) {const id=state.created.find(x=>x.key===item.key).id;const a=current.find(x=>x.id===id);for(const [k,v] of Object.entries(item.record))assert.equal(a[k],v,item.key+' '+k);}
 console.log('All replacement and grouped records verified. Removing only the reviewed original handbook rows.');
 for(const id of plan.deleteIds) {
  if(state.deleted.includes(id))continue;
  await call('/admin/knowledge/'+id,'DELETE');state.deleted.push(id);save();
  if(state.deleted.length%50===0)console.log('Removed '+state.deleted.length+'/'+plan.deleteIds.length+' original rows');
 }
 const after=await call('/admin/knowledge');assert.ok(!after.some(a=>plan.deleteIds.includes(a.id)));
 for(const a of before.filter(x=>!plan.deleteIds.includes(x.id)))assert.equal(hash(after.find(x=>x.id===a.id)),hash(a),'Unrelated record changed: '+a.id);
 fs.writeFileSync(root+'after.json',JSON.stringify(after,null,2));console.log('PASS: '+state.created.length+' consolidated/updated documents, '+state.deleted.length+' reviewed old rows removed; all other records unchanged.');
})().catch(e=>{console.error(e);process.exitCode=1});
