// Policy explanation and live reads only. No applications or HR profile changes.
const assert=require('node:assert/strict');
(async()=>{
 const base='http://localhost:5173/api';const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})});const s=(await login.json()).data;
 const headers={Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId),'Content-Type':'application/json'};
 const read=async path=>{const r=await fetch(base+path,{headers});assert.equal(r.status,200);return (await r.json()).data};const before=await read('/lifecycle/mine'),beforeLeaves=await read('/leave/my');
 const ask=async(message,cid)=>{const r=await fetch(base+'/web-chat/messages',{method:'POST',headers,body:JSON.stringify({message,conversationId:cid})});assert.equal(r.status,200);return (await r.json()).data};
 const cid='policy-dialogue-'+Date.now();let r=await ask('我的年假总共多少天',cid);assert.match(r.answer,/累计工作多久/);assert.doesNotMatch(r.answer,/5天|10天|15天|满1年不满10年/);
 r=await ask('三年',cid);assert.match(r.answer,/连续工作满12个月/);r=await ask('是的',cid);assert.match(r.answer,/\*\*5 天\*\*/);assert.doesNotMatch(r.answer,/10 天|15 天|PDF/);assert.ok(r.answer.length<180);assert.match(r.details,/rsj.sh.gov.cn/);
 r=await ask('查询我的年假余额',cid);assert.equal(r.provider,'assistant-workspace');assert.ok(/核定|暂时没查到/.test(r.answer));
 r=await ask('我累计工作20年，年假有几天',cid+'-known');assert.match(r.answer,/连续工作满12个月/);r=await ask('已经连续满12个月',cid+'-known');assert.match(r.answer,/\*\*15 天\*\*/);
 const travel=cid+'-travel';r=await ask('出差住宿标准是什么',travel);assert.match(r.answer,/哪个城市/);r=await ask('上海',travel);assert.match(r.answer,/普通员工还是中层干部/);r=await ask('普通员工',travel);assert.match(r.answer,/900 元\/人晚/);assert.doesNotMatch(r.answer,/950元|750元|700元/);assert.ok(r.details);
 for(const question of ['年假可以用到什么时候','病假需要什么材料','产假有哪些适用条件','加班和调休有什么规定','员工借款2000元谁审批']){r=await ask(question,cid+'-short-'+Math.random().toString(36).slice(2));assert.ok(r.answer.length<=230,question+' exceeded short reply budget: '+r.answer.length)}
 assert.deepEqual(await read('/lifecycle/mine'),before);assert.deepEqual(await read('/leave/my'),beforeLeaves);
 console.log('PASS: cumulative-tenure interview, natural short answers, real balance, known tenure, travel city/role follow-up and bounded policy responses; no applications created');
})().catch(e=>{console.error(e);process.exitCode=1});
