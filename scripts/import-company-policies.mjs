import {readFile,writeFile,mkdir} from 'node:fs/promises'
const corpus=JSON.parse(await readFile(new URL('../n8nwork/knowledge-files/company-policies-2025.json',import.meta.url),'utf8'))
const base=process.env.HRAGENT_BASE_URL || 'http://localhost:8080/api'
const login=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.HRAGENT_IMPORT_USERNAME||'wanghr',password:process.env.HRAGENT_IMPORT_PASSWORD||'123456'})})
const session=(await login.json()).data
if(!session?.token||session.user.role!=='HR')throw Error('需要当前企业 HR 账号')
const headers={'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)}
async function call(path,method='GET',body){const r=await fetch(base+path,{method,headers,body:body?JSON.stringify(body):undefined});const p=await r.json();if(!r.ok||!p.success)throw Error(p.message||r.status);return p.data}
const old=await call('/admin/knowledge');const retained=new Set();let inserted=0,updated=0,unchanged=0
await mkdir('.artifacts/policies-2025',{recursive:true})
await writeFile('.artifacts/policies-2025/pre-import.json',JSON.stringify(old.filter(a=>a.category==='企业提供制度'),null,2))
for(const c of corpus.chunks){
 const doc=corpus.documents.find(d=>d.code===c.document)
 const source=`${doc.fileName} · PDF第${c.pages.join('、')}页 · ${c.key}`;retained.add(source)
 const content=`适用范围：${c.scope}\n\n问答要点：${c.summary}\n\n条款原文：${c.body}\n\n核对提示：${c.note}\n\n检索词：${c.key} ${c.keywords}`
 const body={category:'企业提供制度',title:c.title.slice(0,160),content,source,region:'全国',reviewStatus:'APPROVED',effectiveFrom:doc.effectiveFrom,effectiveTo:null,publishedAt:null,legalEntities:c.document==='CHR-RS-17 A0'?'上海临港漕河泾人才有限公司':'上海临港漕河泾人才有限公司,上海临港人才有限公司',workTypes:null,jobGrades:null,sourceUrl:null}
 const previous=old.find(a=>a.category===body.category&&a.source===source)
 if(previous&&Object.entries(body).every(([k,v])=>(previous[k]??null)===v)){unchanged++;continue}
 await call(previous?`/admin/knowledge/${previous.id}`:'/admin/knowledge',previous?'PUT':'POST',body)
 if(previous)updated++;else inserted++
}
for(const a of old)if(a.category==='企业提供制度'&&corpus.documents.some(d=>a.source?.startsWith(d.fileName+' · '))&&!retained.has(a.source)&&a.reviewStatus==='APPROVED')await call(`/admin/knowledge/${a.id}`,'PUT',{...a,reviewStatus:'SUPERSEDED'})
const result={tenantId:session.user.tenantId,documents:corpus.documents.map(({rawPages,...d})=>d),sections:corpus.chunks.length,inserted,updated,unchanged}
await writeFile('.artifacts/policies-2025/import-results.json',JSON.stringify(result,null,2));console.log(JSON.stringify({tenantId:result.tenantId,sections:result.sections,inserted,updated,unchanged}))
