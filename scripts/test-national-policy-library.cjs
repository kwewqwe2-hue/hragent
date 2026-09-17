const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const ts=require('../hragentv1/frontend/node_modules/typescript');
const context={exports:{},URL,Intl,Date};vm.createContext(context);vm.runInContext(ts.transpileModule(fs.readFileSync('hragentv1/frontend/src/utils/knowledgeTopics.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,context);
const {policyDomain,policyValidity,groupPolicies}=context.exports;
const plan=JSON.parse(fs.readFileSync('.artifacts/national-policy-library/import-plan.json','utf8'));
const rows=plan.map((p,i)=>({...p.record,id:i+1}));
assert.equal(rows.length,50);assert.ok(rows.every(a=>policyDomain(a)==='public'));
const future=rows.find(a=>a.title==='住房公积金管理条例（2026年修订）'),current=rows.find(a=>a.title==='住房公积金管理条例（2019年修订）');
assert.equal(policyValidity(future,'2026-09-11'),'future');assert.equal(policyValidity(current,'2026-09-11'),'current');
assert.equal(policyValidity(future,'2026-09-20'),'current');assert.equal(policyValidity(current,'2026-09-20'),'historical');assert.equal(groupPolicies([future,current]).length,2);
assert.ok(rows.find(a=>a.title==='防暑降温措施管理办法').content.includes('第二十五条'));
assert.ok(rows.find(a=>a.title==='女职工劳动保护特别规定').content.includes('哺乳期禁忌从事'));
assert.ok(rows.find(a=>a.title==='个人所得税专项附加扣除暂行办法').content.includes('国发〔2023〕13号'));
assert.ok(!rows.some(a=>/网站标识码|ICP备|返回顶部|版权所有/.test(a.content)));
(async()=>{
const base='http://localhost:5173/api',s=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data;
const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
async function call(path,body){const r=await fetch(base+path,{headers,method:body?'POST':'GET',...(body?{body:JSON.stringify(body)}:{})});assert.ok(r.ok,path);const e=await r.json();assert.ok(e.success);return e.data;}
const articles=await call('/admin/knowledge'),published=JSON.parse(fs.readFileSync('.artifacts/national-policy-library/published-results.json','utf8'));
for(const p of published){const a=articles.find(x=>x.id===p.articleId);assert.ok(a,p.catalogId);assert.equal(a.content,plan.find(x=>x.id===p.catalogId).record.content);}
const counts=articles.reduce((out,a)=>(out[policyDomain(a)]=(out[policyDomain(a)]||0)+1,out),{});console.log('Live library: '+JSON.stringify(counts));
if(process.argv.includes('--probe')) {
 for(const [q,expected] of [['国家劳动合同法试用期规定','中华人民共和国劳动合同法'],['全国社会保险经办条例','社会保险经办条例'],['住房公积金管理条例','住房公积金管理条例（2019年修订）'],['2026年节假日安排','2026年部分节假日安排'],['劳动能力鉴定管理办法','劳动能力鉴定管理办法'],['个人所得税专项附加扣除','提高个人所得税有关专项附加扣除标准']]) {
  const a=await call('/employee-services/policy/ask',{message:q});assert.equal(a.status,'MATCHED',q);assert.ok(a.citations.some(c=>c.title.includes(expected)),q);assert.doesNotMatch(a.answer,/适用的公司制度|根据你们提供的制度文件/);assert.ok(a.answer.length<850,q);console.log(JSON.stringify({q,status:a.status,citations:a.citations.map(x=>x.title)}));
 }
 const future=await call('/employee-services/policy/ask',{message:'2026年住房公积金管理条例'});assert.equal(future.status,'NOT_EFFECTIVE');assert.match(future.answer,/2026-09-20/);
 const benefit=await call('/employee-services/policy/ask',{message:'公司有没有企业年金'});assert.match(benefit.answer,/不能直接说有或没有/);
 for(const title of ['社会保险经办条例','劳动能力鉴定管理办法']) {
  const chat=await call('/web-chat/messages',{message:'全国'+title,conversationId:'national-policy-readonly-'+Date.now()});
  assert.ok((chat.answer+'\n'+(chat.details||'')).includes(title),title+' missing from agent reply and expandable evidence');assert.doesNotMatch(chat.answer,/根据你们提供的制度文件/);
 }
}
console.log('PASS: 50 complete main texts, version boundaries, unchanged provenance classification, all documents available to employee knowledge API.');
})().catch(e=>{console.error(e);process.exitCode=1});
