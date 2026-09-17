"""Reviewable, source-specific migration: merge reference fragments, remove superseded extracts."""
from pathlib import Path
import json,re,hashlib,collections
root=Path(__file__).resolve().parents[1];out=root/'.artifacts/hrmanual-cleanup'
before=json.loads((out/'before.json').read_text(encoding='utf-8'));groups=json.loads((out/'groups.json').read_text(encoding='utf-8'))
catalog=json.loads((root/'n8nwork/knowledge-files/national-policy-catalog.json').read_text(encoding='utf-8'))
official=json.loads((root/'.artifacts/national-policy-library/published-results.json').read_text(encoding='utf-8'))
lookup={r['catalogId']:r for r in official}
# Numbers reference the frozen groups.json, not the live query order.
replace={1:'employment',2:'social-insurance',4:'contract-rules',7:'contract',8:'contract',14:'contract',18:'work-hours',19:'labor',20:'monthly-hours',23:'holidays',24:'annual-leave',25:'annual-rules',35:'monthly-hours',47:'occupational-health',50:'female-protection',52:'dispatch',76:'retirement',77:'retirement',78:'social-insurance',81:'flex-retirement',82:'sickness-allowance',92:'work-injury',98:'work-injury',116:'annuity',129:'inspection',132:'social-insurance',140:'arbitration',150:'union',174:'income-tax',175:'tax-deductions',180:'@bonus-tax'}
extra={34:'sh-minimum',36:'@existing-sh-wages',51:'sh-women',121:'sh-contract-three',143:'sh-settlement',145:'sh-points',146:'sh-points',173:'sh-settlement'}
# Remove time-bound tables/notes from the 2022 excerpt library, not all provisions of their underlying laws.
historical={56:'历年小时最低工资表已有新标准',72:'2022年度社保缴费表已过适用年度',89:'2021年药品目录摘编不能作为现行目录',91:'历史生育待遇数额表需使用现行经办标准',99:'劳动能力鉴定已有2025年管理办法',102:'历史年度工伤待遇调整金额',103:'历史年度供养亲属抚恤金调整金额',108:'历史生育津贴最低额摘编',111:'2019公积金年度已结束',112:'与旧公积金年度表相连的比例片段',113:'2022公积金年度已结束',157:'2022阶段性费率摘编已过适用年度',158:'2022阶段性缓缴政策',159:'2022阶段性缓缴政策',160:'2022年复工复产临时行动摘编',161:'2022阶段性缓缴问答',162:'2022缓缴适用行业表',163:'文件明确仅2022年4月至12月',164:'文件明确仅2022年4月至2023年3月',165:'2022疫情稳岗补贴阶段性摘编',166:'旧缓缴问答来源附注',168:'2022复工复产特殊支持摘编',172:'旧实施细则明确有效至2025-11-30；已有沪人才规〔2025〕3号备案记录',201:'2022机构地址电话易变，使用官网实时办事入口'}
evidence={172:'https://service.shanghai.gov.cn/XingZhengWenDangKuJyh/WSDetails.aspx?ID=251215200755YIGiy2XVoLptdEtoR2a',89:'https://www.nhsa.gov.cn/'}
aliases={26:29,32:33,40:41,62:17,63:134,64:134,117:28,118:28,136:17,137:17}
rename={17:'最高人民法院关于审理劳动争议案件适用法律问题的解释（一）',28:'上海市计划生育奖励与补助若干规定（手册摘编）',33:'关于贯彻上海市医疗期标准规定的通知',41:'防暑降温措施管理办法',134:'中华人民共和国出境入境管理法'}
# The heat rule has a complete verified source; the split title represents the same rule.
replace[40]='heat';replace[41]='heat'
table_names={21:'探亲假适用条件与待遇',27:'病假工资与高温津贴',29:'经济补偿适用情形',31:'经济补偿计算',34:'孕期劳动保护与生育保险',35:'哺乳期劳动保护',57:'2022社保缴费表',58:'2022社保缴费表',59:'2022社保缴费表',60:'退休条件历史对照表',64:'养老金历史计发示例',65:'养老金历史计发示例',66:'2022医保待遇表',67:'2022医保待遇表',68:'2022医保待遇表',71:'2022生育保险待遇表',73:'工伤保险费率历史表',82:'工伤待遇支付项目',83:'工伤待遇支付项目',88:'失业保险历史费率表',96:'遗属待遇历史金额表',148:'人才落户旧版条件表'}
remove_table_pages={57,58,59,60,64,65,66,67,68,71,73,88,96,148}
def clean(s):return re.sub(r'(?m)^(条文标题|文号|手册记载执行时间)：[^\n]*\n?','',s).strip()
def topic(title,page):
    patterns=[('证明与办事指南',r'落户|户口|居住证|登记备案|办事|联系方式|出境|入境'),('关怀与合规',r'监察|仲裁|争议|工会|代表大会|安全|保护|保障条例|竞业|民法典|赔偿|补偿|信用|惩戒'),('休假与考勤',r'工时|工作时间|休假|探亲|病假|婚丧|丧假|医疗期|高温|计划生育|防暑|降温'),('薪酬与福利',r'工资|薪金|所得税|扣除|奖金|福利|补助|津贴'),('培训与职业发展',r'培训|技能|学徒|职业学校|终身教育|奖励规定'),('社保与公积金',r'社保|社会保险|养老|医保|医疗保险|生育保险|工伤|年金|公积金'),('入离职与劳动合同',r'劳动|就业|用工|合同|招聘|人事|退职|退休|复工')]
    for t,p in patterns:
        if re.search(p,title):return t
    return '入离职与劳动合同' if page<57 or 97<=page<=120 else '社保与公积金' if page<97 else '关怀与合规'
def region(g):
    # Authority in the PDF outranks a vague title such as “本市”.
    headers=' '.join(x.split('手册记载执行时间：')[0] for x in g['content'])
    return '上海' if re.search(r'上海|沪[府人劳医保公财]|本市',headers) else '全国'
actions=[];retained={}
historical.update({86:'旧医保年度表附注，随旧年度表清理',87:'旧医保年度表附注，随旧年度表清理',88:'旧医保年度退休分组表附注，随旧表清理',151:'中国工会章程已于2023年10月12日修改，旧摘录移除'})
evidence[151]='https://acftu.people.com.cn/n1/2023/1013/c67502-40094626.html'
replace[176]='income-tax'
rename.update({13:'上海招退工登记与社保备案办理提示',123:'上海非因工死亡遗属补助资金渠道摘编',149:'人力资源市场暂行条例'})
table_names[22]='台胞职工出境探亲待遇'
for i,g in enumerate(groups):
    if i in replace:
        rid=replace[i];r=lookup.get(rid,{})
        actions.append(dict(group=i,ids=g['ids'],action='replace',reason='以已核验官方正文替代旧手册的重复或过时摘录，不声称整部旧法均废止',replacement=rid,url=r.get('sourceUrl')));continue
    if i in extra:
        actions.append(dict(group=i,ids=g['ids'],action='replace',reason='已有新版官方文件，移除旧摘编',replacement=extra[i]));continue
    if i in historical:
        actions.append(dict(group=i,ids=g['ids'],action='remove-historical-extract',reason=historical[i],url=evidence.get(i)));continue
    if i==12:
        for aid,page,body in zip(g['ids'],g['pages'],g['content']):
            if page in remove_table_pages:
                actions.append(dict(group=i,ids=[aid],action='remove-historical-table',reason=table_names[page]));continue
            key='table-'+str(page);r=retained.setdefault(key,dict(title=table_names.get(page,'政策表格资料'),ids=[],pages=[],content=[]));r['ids'].append(aid);r['pages'].append(page);r['content'].append(body)
        continue
    j=aliases.get(i,i);key=str(j);r=retained.setdefault(key,dict(title=rename.get(j,groups[j]['title']).rstrip('。'),ids=[],pages=[],content=[]))
    r['ids']+=g['ids'];r['pages']+=g['pages'];r['content']+=g['content']
records=[]
for key,g in retained.items():
    chunks=sorted(zip(g['pages'],g['ids'],g['content']));parts=list(dict.fromkeys(clean(x[2]) for x in chunks));body='\n\n'.join(parts)
    # Preserve unmodified context, but exclude the obsolete bonus-tax sentence from a mixed 2018 notice.
    if key=='179':
        parts=[s for s in parts if '全年一次性奖金' not in s];body='\n\n'.join(parts)
    if key=='191':
        parts=[part for s in parts for part in s.split('●') if '2022年12月31日' not in part and '2020年1月1日起至' not in part];body='\n\n'.join(parts)
    points=[]
    for part in parts:
        for p in re.split(r'●|\n(?=[一二三四五六七八九十]+、)',part):
            p=p.strip();end=p.find('。')
            if 16<=end<=150 and not re.match(r'^[）)；、，]|^\([二三四五六七八九]',p):points.append(p[:end+1])
        if len(points)>=3:break
    summary='\n'.join('• '+s for s in list(dict.fromkeys(points))[:3]) or '本条汇集'+g['title']+'的相关条件和办理说明，请结合下方完整摘编阅读。'
    prefix='内容摘要（2022年手册摘编）\n'+summary+'\n\n版本说明：以下为手册资料整理，尚未逐项核验现行效力，不直接用于确定个人待遇或审批结论。已确认有新版的摘编另行移除或替换。'
    if key=='31':prefix+='医疗期文件的有效期已由沪府〔2025〕20号延长至2030年6月30日，并非因手册记载到期而作废。核对入口：https://www.shanghai.gov.cn/nw12344/20250321/e8ad21ef789c4499aa54bf1fd5f6f972.html'
    if key=='179':prefix+='本条只保留其他税收情形摘编；全年一次性奖金应查看2023年第30号公告。'
    pages=sorted(set(g['pages']));cat=topic(g['title'],pages[0]);reg=region(g)
    if key in ('114','183','184','185','191','192'):cat='财务与审批'
    if key=='149':cat='入离职与劳动合同'
    if key=='193':cat='培训与职业发展'
    if key=='130':cat='关怀与合规'
    if key.startswith('table-'):reg='上海' if pages[0] in (27,34,35,82,83) else '全国'
    record=dict(title=g['title'],category=cat,content=prefix+'\n\n原手册相关内容\n'+body+'\n\n出处：hrmanual.pdf（2022年版），PDF第'+','.join(map(str,pages))+'页。',source='hrmanual.pdf · 政策主题摘编（2022资料）',region=reg,reviewStatus='REFERENCE',publishedAt=None,effectiveFrom=None,effectiveTo=None,sourceUrl=None,jobGrades=None,workTypes=None,legalEntities=None)
    records.append(dict(key=key,record=record,oldIds=g['ids'],pages=pages));actions.append(dict(group=key,ids=g['ids'],action='merge-reference',reason='按政策合并并归类，现行效力待核验'))
# Manual review fallback: public tax notice was read using the web tool; robots prevented automated capture.
extra_rows=json.loads((root/'n8nwork/knowledge-files/hrmanual-replacements.json').read_text(encoding='utf-8'));new=[]
for r in extra_rows:
    s=json.loads((out/'sources'/ (r['id']+'.json')).read_text(encoding='utf-8'))
    if r['id']=='bonus-tax':
        body='官方政策摘要：符合公告所列条件的居民个人，全年一次性奖金可以选择单独计税，也可以并入当年综合所得。本公告执行至2027年12月31日。具体税额需核对官方原文及其按月换算的综合所得税率表。\n2022年手册中“自2022年1月1日起均应并入综合所得”的旧结论不再作为现行回答。'
    else:
        assert s.get('ok'),r['id'];raw=s['content'];start=raw.find(r['start']);end=raw.find(r['end'],start);assert start>=0 and end>=start,r['id'];body=raw[start:end+len(r['end'])]
    rec={k:r.get(k) for k in ['title','category','region','effectiveFrom','effectiveTo']};rec.update(content=body,source='官方政策更新 · 2022手册核对',sourceUrl=r['url'],reviewStatus='APPROVED',publishedAt=None,jobGrades=None,workTypes=None,legalEntities=None)
    new.append(dict(key=r['id'],record=rec,sourceSha256=s.get('sha256'),reviewedOn='2026-09-11'))
handbook=[a for a in before if a.get('source','').startswith('hrmanual.pdf')]
covered=[aid for a in actions for aid in a['ids']];assert len(covered)==len(set(covered))==457
assert set(covered)=={a['id'] for a in handbook if a['reviewStatus']=='APPROVED'}
plan=dict(retained=records,official=new,actions=actions,deleteIds=[a['id'] for a in handbook],expectedCompanyUntouched=True)
(out/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'oldHandbookRows':len(handbook),'oldVisibleFragments':457,'mergedReferenceDocuments':len(records),'officialReplacements':len(new),'actions':dict(collections.Counter(a['action'] for a in actions)),'topics':dict(collections.Counter(x['record']['category'] for x in records))},ensure_ascii=False,indent=2))
