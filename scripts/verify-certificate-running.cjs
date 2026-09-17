// Read-only deployed smoke checks, apart from ordinary demo login/logout sessions.
const assert=require('node:assert/strict'),fs=require('node:fs/promises'),crypto=require('node:crypto');
const root='http://localhost:5173';
(async()=>{
 let ready=false;for(let i=0;i<25;i++){try{if((await fetch(root+'/api/health',{signal:AbortSignal.timeout(2000)})).ok){ready=true;break}}catch{}await new Promise(r=>setTimeout(r,1000))}assert(ready,'backend health');
 for(const [url,dist,prefix]of[['/agent/','hragent-chat/dist/','/agent/'],['/certificates','hragentv1/frontend/dist/','/']]){
  const html=await(await fetch(root+url)).text(),asset=html.match(/src="([^"]+\.js)"/)[1];const local=await fs.readFile(dist+asset.replace(prefix,''));const hosted=Buffer.from(await(await fetch(root+asset)).arrayBuffer());assert.equal(crypto.createHash('sha256').update(hosted).digest('hex'),crypto.createHash('sha256').update(local).digest('hex'));
 }
 for(const username of ['zhangsan','wanghr']){
  const login=await(await fetch(root+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password:'123456'})})).json();assert(login.success);const user=login.data.user,headers={Authorization:'Bearer '+login.data.token,'X-Workspace-Id':String(user.tenantId)};
  try{
   for(const endpoint of ['/employment-certificates/my','/employment-certificate-templates','/lifecycle/mine']){const r=await fetch(root+'/api'+endpoint,{headers});assert.equal(r.status,200,endpoint);assert(Array.isArray((await r.json()).data))}
   const config=await fetch(root+'/api/employment-certificates/esign/config',{headers});assert.equal(config.status,user.role==='HR'?200:403);
   if(user.role==='HR'){const data=(await config.json()).data;assert(!Object.hasOwn(data,'secretKey')&&!Object.hasOwn(data,'secretId'));console.log('PASS HR electronic signature settings: ready='+data.hasCredentials+', enabled='+data.enabled)}
   assert.equal((await fetch(root+'/api/employment-certificates/my',{headers:{...headers,'X-Workspace-Id':'99999999'}})).status,403);
   console.log('PASS deployed '+user.role+': certificate records, template visibility, service records and tenant restrictions');
  }finally{await fetch(root+'/api/auth/logout',{method:'POST',headers})}
 }
 console.log('PASS deployed builds match tested frontend and chat assets; no certificate submissions, approvals or cloud signing performed.');
})().catch(e=>{console.error(e.message);process.exitCode=1});
