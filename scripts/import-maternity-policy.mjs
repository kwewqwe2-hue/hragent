const base='http://localhost:8080/api'
const session=(await (await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.HRAGENT_IMPORT_USERNAME||'wanghr',password:process.env.HRAGENT_IMPORT_PASSWORD||'123456'})})).json()).data
if(!session?.token||session.user.role!=='HR') throw new Error('需要本企业HR账号')
const headers={'Content-Type':'application/json',Authorization:`Bearer ${session.token}`,'X-Workspace-Id':String(session.user.tenantId)}
async function call(path,method='GET',body){const r=await fetch(base+path,{method,headers,body:body?JSON.stringify(body):undefined});const p=await r.json();if(!r.ok||!p.success)throw Error(p.message);return p.data}
const national='https://xzfg.moj.gov.cn/front/law/detail?LawID=343'
const rows=[
 {title:'流产假天数（全国）',region:'全国',effectiveFrom:'2012-04-28',source:'国家行政法规库·第七条',sourceUrl:national,content:'怀孕未满 4 个月流产的，享受 15 天产假；怀孕满 4 个月流产的，享受 42 天产假。'},
 {title:'产假天数与主要条件（全国）',region:'全国',effectiveFrom:'2012-04-28',source:'国家行政法规库·第七条',sourceUrl:national,content:'国家规定的基础产假是 **98 天**，其中可在产前休 15 天。难产增加 15 天；多胞胎每多生育一个婴儿，再增加 15 天。'},
 {title:'产假与生育假天数（上海）',region:'上海',effectiveFrom:'2021-11-25',source:'上海市人口与计划生育条例·第三十一条',sourceUrl:'https://www.shanghai.gov.cn/jcsfbrkcqjhfzzh/20230621/c440fb200a9b48da87aed931e369792f.html',content:'适用上海政策、符合法律法规规定生育的，通常可休 **158 天**：国家产假 98 天，加上海生育假 60 天。'},
 {title:'产假工资与生育津贴待遇（全国）',region:'全国',effectiveFrom:'2012-04-28',source:'国家行政法规库·第八条',sourceUrl:national,content:'已参加生育保险的，生育津贴按单位上年度职工月平均工资标准，由生育保险基金支付；未参保的，由单位按产假前工资标准支付。具体到账金额还需核对当地规定和个人参保情况。'}
]
const existing=await call('/admin/knowledge')
for(const row of rows){const body={...row,category:'法定休假政策',reviewStatus:'APPROVED',publishedAt:row.effectiveFrom};const old=existing.find(a=>a.category===body.category&&a.title===body.title&&a.sourceUrl===body.sourceUrl);if(old)continue;await call('/admin/knowledge','POST',body)}
console.log('Verified maternity policy references added to existing tenant knowledge base; existing records preserved.')
