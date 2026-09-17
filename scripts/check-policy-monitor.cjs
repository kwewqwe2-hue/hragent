const assert=require('node:assert/strict');
const base='http://localhost:8080/api';
async function login(username){const r=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})});assert.equal(r.status,200);return (await r.json()).data;}
function headers(s){return {'Content-Type':'application/json',Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId)}}
async function request(s,path,method='GET'){const r=await fetch(base+path,{method,headers:headers(s)});assert.equal(r.status,200);return (await r.json()).data;}
(async()=>{const hr=await login('wanghr');
 if(process.argv.includes('--start')){const employee=await login('zhangsan');const denied=await fetch(base+'/admin/policy-monitor/scan',{method:'POST',headers:headers(employee)});assert.equal(denied.status,403);console.log(await request(hr,'/admin/policy-monitor/scan','POST'));}
 console.log(JSON.stringify(await request(hr,'/admin/policy-monitor/sources'),null,2));
 const candidates=(await request(hr,'/admin/policy-monitor/candidates')).filter(c=>c.sourceId.startsWith('official-'));
 console.log(JSON.stringify({officialCandidates:candidates.length,records:candidates.slice(0,8).map(c=>({title:c.title,status:c.reviewStatus,effectiveAt:c.effectiveAt,url:c.sourceUrl}))},null,2));
})().catch(e=>{console.error(e.message);process.exitCode=1});
