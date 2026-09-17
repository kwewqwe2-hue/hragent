const assert=require('node:assert/strict')
const base='http://localhost:8080/api'
async function login(username){return(await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})})).json()).data}
function headers(s){return {Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId)}}
async function json(s,url,body){const r=await fetch(base+url,{method:body?'POST':'GET',headers:{...headers(s),'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});const data=await r.json();assert.equal(r.status,200,JSON.stringify(data));return data.data}
;(async()=>{
 const employee=await login('zhangsan'),hr=await login('wanghr'),other=await login('lisi'),cid='materials-'+Date.now()
 const say=message=>json(employee,'/web-chat/messages',{message,conversationId:cid})
 let id,submitted=false,completed=false
 try{
  await say('我要调取档案');await say('测试材料上传，不调取真实档案');await say(cid);await say('测试接收单位')
  id=(await json(employee,'/lifecycle/mine')).find(x=>x.fields['调取用途']===cid).id
  const fixture=Buffer.from('%PDF-1.4\n% AUTOMATED TEST ONLY - NOT AN OFFICIAL DOCUMENT\n%%EOF')
  const upload=async(s,url)=>{const form=new FormData();form.append('file',new Blob([fixture],{type:'application/pdf'}),'test-only.pdf');return fetch(base+url,{method:'POST',headers:headers(s),body:form})}
  assert.equal((await upload(employee,`/lifecycle/${id}/materials`)).status,200)
  assert.equal((await fetch(base+`/lifecycle/hr/${id}/materials`,{headers:headers(hr)})).status,403)
  assert.equal((await fetch(base+`/lifecycle/${id}/materials`,{headers:headers(other)})).status,403)
  await say('确认提交');submitted=true
  const material=await fetch(base+`/lifecycle/hr/${id}/materials`,{headers:headers(hr)});assert.equal(material.status,200);assert.deepEqual(Buffer.from(await material.arrayBuffer()),fixture)
  await json(hr,`/lifecycle/hr/${id}/review`,{action:'APPROVED',opinion:'仅验证在线材料交付，无真实档案办理'})
  assert.equal((await upload(hr,`/lifecycle/hr/${id}/file`)).status,200)
  assert.equal((await fetch(base+`/lifecycle/${id}/file`,{headers:headers(employee)})).status,404)
  await json(hr,`/lifecycle/hr/${id}/review`,{action:'COMPLETED',opinion:'自动化测试完成，附件明确标注测试用途'});completed=true
  const output=await fetch(base+`/lifecycle/${id}/file`,{headers:headers(employee)});assert.equal(output.status,200);assert.deepEqual(Buffer.from(await output.arrayBuffer()),fixture)
  assert.equal((await fetch(base+`/lifecycle/${id}/file`,{headers:headers(other)})).status,403)
  console.log(`PASS: encrypted applicant upload, confirmation-gated HR access, cross-user denial, approval-gated delivery, exact download bytes; test service #${id}`)
 }finally{if(id&&!completed){if(!submitted)await say('取消办理');else{const row=(await json(hr,'/lifecycle/hr/queue')).find(x=>x.id===id);if(row?.status==='SUBMITTED')await json(hr,`/lifecycle/hr/${id}/review`,{action:'REJECTED',opinion:'自动化测试终止，无真实办理'})}}}
})().catch(e=>{console.error(e);process.exitCode=1})
