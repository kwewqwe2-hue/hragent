const assert=require('node:assert/strict');
(async()=>{
 const base='http://localhost:5173/api',s=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data;
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
 async function call(path,body){const r=await fetch(base+path,{headers,method:body?'POST':'GET',...(body?{body:JSON.stringify(body)}:{})});assert.equal(r.status,200);return (await r.json()).data;}
 const profile=await call('/employee-services/profile');const before=await call('/lifecycle/mine');
 for(const [q,url,effectiveTo] of [
  ['上海工资支付办法','https://rsj.sh.gov.cn/tgzfl_17732/20260731/t0035_1442850.html','2031-07-31'],
  ['上海失业保险金支付标准','https://rsj.sh.gov.cn/tshbx_17729/20260702/t0035_1442080.html','2028-06-30'],
  ['北京2026社保缴费基数','https://rsj.beijing.gov.cn/xxgk/2024zcwj/202608/t20260821_4831461.html',null],
  ['北京2026城乡居民养老保险缴费标准','https://rsj.beijing.gov.cn/xxgk/2024zcwj/202603/t20260327_4567778.html',null]
 ]){
  const a=await call('/employee-services/policy/ask',{message:q});assert.equal(a.status,'MATCHED',q+' '+a.answer);const citation=a.citations.find(c=>c.sourceUrl===url);assert.ok(citation,q);assert.equal(citation.effectiveTo,effectiveTo);assert.equal(a.profile.location,profile.location);console.log('PASS: '+q+' uses reviewed source and preserves employee profile');
 }
 const p=await call('/policy-monitor/progress');assert.equal(p.approvedCount,10);assert.equal(p.pendingCount,14);
 assert.deepEqual(await call('/lifecycle/mine'),before);assert.deepEqual(await call('/employee-services/profile'),profile);
 console.log('PASS: ten published records, fourteen still pending, expiry/source citations and no employee business changes');
})().catch(e=>{console.error(e);process.exitCode=1});
