const fs=require('node:fs');
(async()=>{
 const root='.artifacts/hrmanual-cleanup';fs.mkdirSync(root,{recursive:true});
 const base='http://localhost:5173/api';const e=await(await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'wanghr',password:'123456'})})).json();
 const s=e.data;if(s?.user.role!=='HR')throw Error('HR login required');
 const r=await fetch(base+'/admin/knowledge',{headers:{Authorization:'Bearer '+s.token,'X-Workspace-Id':String(s.user.tenantId)}});const a=await r.json();if(!r.ok||!a.success)throw Error('Cannot read knowledge');
 fs.writeFileSync(root+'/before.json',JSON.stringify(a.data,null,2));
 const refs=a.data.filter(x=>x.source?.startsWith('hrmanual.pdf'));
 console.log(JSON.stringify({total:a.data.length,handbook:refs.length,statuses:refs.reduce((o,x)=>(o[x.reviewStatus]=(o[x.reviewStatus]||0)+1,o),{})}));
 const grouped=new Map();for(const x of refs.filter(x=>x.reviewStatus==='APPROVED')){const t=x.title.replace(/^手册第\d+页[｜|]/,'');let g=grouped.get(t)||{title:t,ids:[],pages:[],content:[]};g.ids.push(x.id);g.pages.push(Number(x.source.match(/PDF第(\d+)/)?.[1]));g.content.push(x.content);grouped.set(t,g);}
 const groups=[...grouped.values()];fs.writeFileSync(root+'/groups.json',JSON.stringify(groups,null,2));
 console.log(groups.map((g,i)=>`${i}: [${g.pages.join(',')}] ${g.title}`).join('\n'));
})().catch(e=>{console.error(e);process.exitCode=1});
