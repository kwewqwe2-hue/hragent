// Explicitly reviewed public documents only. Dry-run by default; never changes employee business records.
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const selections=[
 [1,'80639b2abf49','2026-08-16','2031-08-15','2026-08-06','第十七条明确生效和截止日期；正文第1至16条保留申领条件、停止发放及地区限制；无独立下载附件。'],
 [3,'5cf5a90518bc','2026-08-16','2031-08-15','2026-07-20','修订后通知第十二条明确期限；保留全部适用条件和引用法规，不据标题推断所有劳动争议处理结果；无独立下载附件。'],
 [4,'79288299278c','2026-08-01','2031-07-31','2026-07-15','第二十七条明确期限；正文明确修订沪人社综发〔2016〕29号，核对第1条适用范围及工资支付条款；无独立下载附件。'],
 [5,'e99a73051546','2026-07-01','2028-06-30','2026-06-30','第二条明确期限及沪人社规〔2025〕9号同时废止；保留第1至12个月、第13至24个月和延长领取三个条件；无独立下载附件。'],
 [9,'afb6f1ca9b29','2026-01-01','2030-12-31','2026-01-21','第十一条明确期限，第2条限定上海参保用人单位；正文包含各档费率与限制条件，无独立下载附件。'],
 [11,'74558d87de53','2026-01-01','2026-12-31','2026-01-09','末段明确期限；仅适用于2025年底前已办理按月领取城乡居民养老金手续人员，不扩展为普通员工福利；无独立下载附件。'],
 [12,'4ab910057cc8','2026-01-01','2030-12-31','2025-12-23','第四条明确期限；第1条限定经相关部门确定的低保、特困和优抚对象；无独立下载附件。'],
 [15,'e63365b875ba','2026-08-21',null,'2026-08-21','官网元数据实施日期为2026-08-21；正文明确2026年7月起的基数及补差期限，保留追溯适用说明。2025年度通告不作为2026年度基数依据；无附件，未声称永久有效。'],
 [16,'f5325bb395ba','2026-03-25',null,'2026-03-27','官网实施日期2026-03-25，正文仅为2026年度北京城乡居民养老保险缴费标准；不得当作企业职工社保标准。无独立附件；未人为推定法规废止日。'],
 [20,'2a69c431b84e','2026-01-01',null,'2025-12-25','正文第五部分明确2026-01-01起实施，原文公开属性和落款为2025-12-25，纠正栏目迁移日期；此前不一致政策按本通知，保留援助对象条件；无独立下载附件。']
];
(async()=>{
 const base='http://localhost:5173/api',r=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'wanghr',password:'123456'})});assert.equal(r.status,200);const s=(await r.json()).data;assert.equal(s.user.tenantId,1);
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
 async function call(path,body){const res=await fetch(base+path,{headers,method:body?'POST':'GET',...(body?{body:JSON.stringify(body)}:{})});assert.equal(res.status,200,path);const envelope=await res.json();assert.equal(envelope.success,true);return envelope.data;}
 const rows=await call('/admin/policy-monitor/candidates'),snapshot=JSON.parse(fs.readFileSync('.artifacts/policy-review/candidates.json','utf8'));
 const plan=selections.map(([id,version,effectiveAt,effectiveTo,publishedAt,note])=>{
  const c=rows.find(x=>x.id===id),old=snapshot.find(x=>x.id===id);assert.ok(c&&old);assert.equal(c.sourceUrl,old.sourceUrl);assert.equal(c.version,version);assert.equal(c.contentHash,old.contentHash);assert.equal(crypto.createHash('sha256').update(c.title+'\n'+c.content).digest('hex'),c.contentHash);
  return {id,title:c.title,url:c.sourceUrl,contentHash:c.contentHash,state:c.reviewStatus,body:{decision:'APPROVED',effectiveAt,effectiveTo,publishedAt,region:c.region,opinion:'用户授权的知识库维护；2026-09-11通过官方原文逐项核验。'+note+'未据此改动任何公司制度或员工权益记录。'}};
 });
 fs.writeFileSync('.artifacts/policy-review/verified-plan.json',JSON.stringify(plan,null,2));
 if(!process.argv.includes('--apply')){console.log(JSON.stringify(plan.map(x=>({id:x.id,title:x.title,...x.body})),null,2));return;}
 const results=[];
 for(const p of plan){const result=p.state==='PENDING_REVIEW'?await call('/admin/policy-monitor/candidates/'+p.id+'/review',p.body):rows.find(x=>x.id===p.id);assert.equal(result.reviewStatus,'APPROVED');results.push({candidateId:p.id,knowledgeArticleId:result.knowledgeArticleId,title:p.title});}
 const articles=await call('/admin/knowledge');for(const p of plan){const id=results.find(x=>x.candidateId===p.id).knowledgeArticleId,a=articles.find(x=>x.id===id);assert.ok(a);assert.equal(a.effectiveFrom,p.body.effectiveAt);assert.equal(a.effectiveTo,p.body.effectiveTo);assert.equal(a.publishedAt,p.body.publishedAt);assert.equal(a.sourceUrl,p.url);}
 fs.writeFileSync('.artifacts/policy-review/published-results.json',JSON.stringify(results,null,2));console.log(JSON.stringify({published:results.length,records:results},null,2));
})().catch(e=>{console.error(e);process.exitCode=1});
