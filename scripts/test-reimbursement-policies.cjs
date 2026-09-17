const fs=require('node:fs'),assert=require('node:assert/strict'),vm=require('node:vm'),ts=require('../hragentv1/frontend/node_modules/typescript');
(async()=>{
const dir='.artifacts/reimbursement-review/',plan=JSON.parse(fs.readFileSync(dir+'plan.json','utf8')),state=JSON.parse(fs.readFileSync(dir+'applied.json','utf8')),before=JSON.parse(fs.readFileSync(dir+'before.json','utf8'));
const base='http://localhost:5173/api',s=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data;
const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':'1','Content-Type':'application/json'};
async function call(path,body){const r=await fetch(base+path,{headers,method:body?'POST':'GET',...(body?{body:JSON.stringify(body)}:{})});const e=await r.json();assert.ok(r.ok&&e.success,path);return e.data;}
const all=await call('/admin/knowledge');const context={exports:{},URL};vm.createContext(context);vm.runInContext(ts.transpileModule(fs.readFileSync('hragentv1/frontend/src/utils/knowledgeTopics.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,context);
for(const item of plan){const a=all.find(a=>a.id===state.find(x=>x.key===item.key).id);assert.ok(a,item.key);for(const[k,v]of Object.entries(item.record))assert.equal(a[k],v);assert.equal(context.exports.policyDomain(a),'public');assert.ok(context.exports.topicIds(a).includes('travel'));assert.doesNotMatch(a.content,/ICP备|用户登录|热门检索/);}
assert.equal(plan.length,7);const old=all.find(a=>a.id===1091);assert.equal(old.effectiveFrom,'2020-03-23');assert.ok(old.content.endsWith(before.find(a=>a.id===1091).content));
for(const a of before.filter(a=>a.category==='企业提供制度'))assert.deepEqual(all.find(x=>x.id===a.id),a);
const work=all.find(a=>a.title==='会计信息化工作规范（2025年起施行）');assert.match(work.content,/第五十条/);assert.match(work.content,/第二十七条/);assert.equal(work.effectiveFrom,'2025-01-01');
for(const item of plan.filter(p=>['shared-2026','validation-2026'].includes(p.key)))assert.equal(item.record.effectiveFrom,null,'Do not invent legal commencement date');
const answer=await call('/employee-services/policy/ask',{message:'会计信息化工作规范'});assert.equal(answer.status,'MATCHED');assert.ok(answer.citations.some(c=>c.id===work.id));assert.ok(answer.answer.length<850);
console.log('PASS: 7 reviewed reimbursement records in public travel topic, unchanged company rules and original 2020 text/date, complete 50-article PDF, no invented commencement dates, live official-policy retrieval.');
})().catch(e=>{console.error(e);process.exitCode=1});
