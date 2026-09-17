// Explicitly reviewed official text only. Repeat runs are idempotent; no employee workflow mutations.
const fs=require('node:fs'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root='.artifacts/national-policy-library/';
const hash=x=>crypto.createHash('sha256').update(typeof x==='string'?x:JSON.stringify(x)).digest('hex');
(async()=>{
 const plan=JSON.parse(fs.readFileSync(root+'import-plan.json','utf8'));
 assert.ok(plan.length>=40);
 for(const p of plan){assert.ok(p.reviewed,'Snapshot not reviewed: '+p.id);assert.equal(hash(p.record.content),p.bodySha256);const u=new URL(p.record.sourceUrl);assert.equal(u.protocol,'https:');assert.ok(u.hostname.endsWith('.gov.cn'));assert.ok(p.record.effectiveFrom);assert.ok(p.record.source.startsWith('全国政策原文 · '));}
 const base=process.env.HRAGENT_BASE_URL||'http://localhost:5173/api';
 const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.HRAGENT_IMPORT_USERNAME||'wanghr',password:process.env.HRAGENT_IMPORT_PASSWORD||'123456'})});
 assert.equal(login.status,200);const s=(await login.json()).data;assert.equal(s.user.role,'HR');assert.equal(s.user.tenantId,1,'Explicitly targets demo workspace 1');
 const headers={'Content-Type':'application/json',Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId)};
 async function call(path,method='GET',body){const res=await fetch(base+path,{headers,method,...(body?{body:JSON.stringify(body)}:{})});const e=await res.json();assert.ok(res.ok&&e.success,path+' '+(e.message||res.status));return e.data;}
 const before=await call('/admin/knowledge');
 fs.writeFileSync(root+'before-import.json',JSON.stringify(before,null,2));
 const actions=plan.map(p=>({...p,existing:before.find(x=>x.title===p.record.title&&x.sourceUrl===p.record.sourceUrl&&x.source.startsWith('全国政策原文 · '))}));
 if(!process.argv.includes('--apply')){console.log(JSON.stringify({verified:plan.length,toCreate:actions.filter(x=>!x.existing).length,toUpdate:actions.filter(x=>x.existing).length}));return;}
 const results=[];
 for(const p of actions){
   let a=p.existing;
   if(!a)a=await call('/admin/knowledge','POST',p.record);
   else if(Object.entries(p.record).some(([k,v])=>a[k]!==v))a=await call('/admin/knowledge/'+a.id,'PUT',p.record);
   results.push({catalogId:p.id,articleId:a.id,title:p.record.title,sourceUrl:p.record.sourceUrl,bodySha256:p.bodySha256});
   fs.writeFileSync(root+'published-results.json',JSON.stringify(results,null,2));
   console.log('Published: '+p.id);
 }
 const after=await call('/admin/knowledge');
 for(const p of plan){const a=after.find(x=>x.id===results.find(x=>x.catalogId===p.id).articleId);for(const [k,v] of Object.entries(p.record))assert.equal(a[k],v,p.id+' '+k);}
 const touched=new Set(results.map(x=>x.articleId));
 for(const a of before.filter(x=>!touched.has(x.id)))assert.equal(hash(after.find(x=>x.id===a.id)),hash(a),'Unrelated record changed: '+a.id);
 console.log('PASS: '+results.length+' verified policies stored, no unrelated/company/reference records changed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
