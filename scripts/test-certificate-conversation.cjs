const assert=require('node:assert/strict')
const base='http://localhost:8080/api'
;(async()=>{
 const session=(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'zhangsan',password:'123456'})})).json()).data
 const headers={'Content-Type':'application/json',Authorization:'Bearer '+session.token,'X-Workspace-Id':String(session.user.tenantId)}
 async function call(url,body,method){const r=await fetch(base+url,{headers,method:method||(body?'POST':'GET'),body:body?JSON.stringify(body):undefined});const data=await r.json();assert.equal(r.status,200,JSON.stringify(data));return data.data}
 const pending=[]
 try{
  const examples=[
   ['STANDARD',['买房需要在职证明','自动化验收：购房用途，不用于实际办理','中文','不需要','联调测试']],
   ['VISA',['我要开出国签证在职证明','日本','日本驻上海总领事馆','自动化验收：旅游签证，不用于实际办理','中英双语','需要','联调测试']],
   ['INCOME',['我要开收入证明','自动化验收：购房贷款，不用于实际办理','联调测试']]
  ]
  for(const [type,messages] of examples){
   const conversationId='certificate-'+type+'-'+Date.now();const say=message=>call('/web-chat/messages',{message,conversationId})
   const before=await call('/employment-certificates/my')
   for(const message of messages){const result=await say(message);assert.equal(result.provider,'hrssc-lifecycle')}
   assert.equal((await call('/employment-certificates/my')).length,before.length)
   const result=await say('确认提交');assert.match(result.answer,/原有证明管理/)
   const serviceId=Number(result.answer.match(/#(\d+)/)[1]);const service=(await call('/lifecycle/mine')).find(x=>x.id===serviceId);assert.ok(service.certificateRequestId);pending.push(service.certificateRequestId)
   const original=(await call('/employment-certificates/my')).find(x=>x.id===service.certificateRequestId);assert.equal(original.certificateType,type);assert.equal(original.status,'PENDING_HR')
   if(type==='VISA'){assert.equal(original.language,'BILINGUAL');assert.equal(original.destinationCountry,'日本');assert.equal(original.includeSalary,true)}
   if(type==='STANDARD')assert.equal(original.includeSalary,false)
   const count=(await call('/employment-certificates/my')).length;await say('确认提交');assert.equal((await call('/employment-certificates/my')).length,count)
   await call(`/employment-certificates/${original.id}/cancel`,{},'PUT');pending.splice(pending.indexOf(original.id),1)
   const updated=(await call('/lifecycle/mine')).find(x=>x.id===serviceId);assert.equal(updated.status,'CANCELLED');assert.equal(updated.statusLabel,'已取消')
  }
  console.log('PASS: purchase employment / visa / income conversations create original certificate requests only after confirmation; field mapping, idempotency and original status sync; all test requests cancelled')
 }finally{for(const id of pending)await call(`/employment-certificates/${id}/cancel`,{},'PUT')}
})().catch(e=>{console.error(e);process.exitCode=1})
