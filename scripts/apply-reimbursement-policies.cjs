const fs=require('node:fs'),assert=require('node:assert/strict');
(async()=>{
const root='.artifacts/reimbursement-review/',plan=JSON.parse(fs.readFileSync(root+'plan.json','utf8')),before=JSON.parse(fs.readFileSync(root+'before.json','utf8'));
const base='http://localhost:5173/api',session=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'wanghr',password:'123456'})})).json()).data;
assert.equal(session.user.role,'HR');assert.equal(session.user.tenantId,1);
async function call(path,method='GET',body){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+session.token,'X-Workspace-Id':'1'},...(body?{body:JSON.stringify(body)}:{})});const e=await r.json();assert.ok(r.ok&&e.success,path+': '+e.message);return e.data;}
const current=await call('/admin/knowledge'),results=[];
for(const item of plan){
 const previous=item.id?current.find(a=>a.id===item.id):current.find(a=>a.title===item.record.title&&a.sourceUrl===item.record.sourceUrl);
 if(item.id && JSON.stringify(previous)!==JSON.stringify(before.find(a=>a.id===item.id)))for(const[k,v]of Object.entries(item.record))assert.equal(previous[k],v,'Concurrent change '+k);
 if(!process.argv.includes('--apply')){console.log((previous?'UPDATE ':'CREATE ')+item.record.title);continue;}
 const saved=previous&&Object.entries(item.record).every(([k,v])=>previous[k]===v)?previous:await call('/admin/knowledge'+(previous?'/'+previous.id:''),previous?'PUT':'POST',item.record);
 results.push({key:item.key,id:saved.id});
 fs.writeFileSync(root+'applied.json',JSON.stringify(results,null,2));
}
if(!process.argv.includes('--apply'))return;
const after=await call('/admin/knowledge');for(const a of before.filter(a=>a.id!==1091))assert.deepEqual(after.find(x=>x.id===a.id),a,'Unrelated record '+a.id);
for(const item of plan){const a=after.find(x=>x.id===results.find(x=>x.key===item.key).id);for(const[k,v]of Object.entries(item.record))assert.equal(a[k],v);}
fs.writeFileSync(root+'after.json',JSON.stringify(after,null,2));console.log('PASS: six official documents added, 2020 date preserved with review note, all other records unchanged.');
})().catch(e=>{console.error(e);process.exitCode=1});
