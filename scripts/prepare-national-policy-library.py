"""Prepare source-preserving, versioned policy documents. Requires reviewed SHA per record to publish."""
from pathlib import Path
from urllib.parse import urljoin, urlparse
import hashlib, json, re

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / '.artifacts/national-policy-library'
catalog = json.loads((ROOT / 'n8nwork/knowledge-files/national-policy-catalog.json').read_text(encoding='utf-8'))
last = dict(zip(
    'labor contract social-insurance arbitration annual-leave annual-rules work-hours holidays medical-period female-protection work-injury housing-fund contract-rules unemployment inspection social-services min-wage wages dispatch annuity flex-retirement migrant-wages child-labor women-rights union occupational-health privacy income-tax medical-transfer vocational-education safety disputes-interpretation-2 heat housing-fund-current archives employment pension-transfer e-sign sickness-allowance invoice tax-deductions mental-health capacity-assessment'.split(),
    [107,98,98,54,10,19,9,8,9,16,67,50,38,33,36,63,15,20,29,32,14,64,14,86,58,88,74,22,17,69,119,21,25,47,33,69,13,36,14,43,32,85,39]))

def han(n):
    if n < 10: return '零一二三四五六七八九'[n]
    if n < 20: return '十' + (han(n % 10) if n % 10 else '')
    if n < 100: return han(n // 10) + '十' + (han(n % 10) if n % 10 else '')
    return han(n // 100) + '百' + (('零' if n % 100 < 10 else '一' if n % 100 < 20 else '') + han(n % 100) if n % 100 else '')

notes = {
 'labor':'本法涉及工作时间、退休和社会保险的规定，应结合现行工时规定、社会保险法和渐进式延迟退休决定共同适用。',
 'social-insurance':'涉及法定退休年龄与最低缴费年限的条款，须同时核对2025年起实施的渐进式延迟退休决定，不能按旧年龄统一回答。费率、缴费基数和具体待遇还需核对参保地政策。',
 'medical-period':'医疗期不是自动批准的病假天数；需结合工作年限、公司请假流程和证明材料。病退、退职条款须结合2025年起实施的企业职工基本养老保险病残津贴暂行办法，不直接套用旧待遇。',
 'unemployment':'条例费率、领取条件与办理时限还应结合社会保险法及现行配套政策；不将条例历史费率直接作为2026年实际缴费率。',
 'min-wage':'本规定为最低工资制定与执行规则，不是当地2026年最低工资数额。附录为历史测算示例；工资折算需结合人社部发〔2025〕2号，具体金额查询工作地现行标准。',
 'retirement':'收录决定及办法正文；个人出生年月对应的退休年龄须核对官方附表，不能仅凭标题或概括推算。最低缴费年限调整从2030年开始，不能提前套用。',
 'income-tax':'收录法律正文。官方页面另附税率表图片，请打开原文核对附表后计算，不能仅凭正文推算税率。专项附加扣除需同时核对2022年及2023年配套文件。',
 'tax-deductions':'本办法中的子女教育、赡养老人额度已由国发〔2023〕13号调整；3岁以下婴幼儿照护由国发〔2022〕8号增设。现行金额须按配套文件，不能将原文旧金额直接用于2026年计算。',
 'infant-tax':'本通知增设婴幼儿照护扣除；原文1000元标准已由国发〔2023〕13号自2023年1月1日起提高至2000元。',
 'medical-transfer':'收录办法正文，办理流程图及信息表保留官方附件入口；正式办理请使用经办机构提供的现行表单。',
 'annuity':'这是国家层面的年金规则，不代表本公司已设立年金。公司设立状态以已核实的企业福利文件为准，不据国家文件推断有或没有。',
 'e-reimbursement':'本通知规范电子凭证报销入账归档，不设定本公司的差旅标准、报销额度或审批人；这些仍按适用的公司制度办理。',
 'housing-fund':'2026年修订版本自2026年9月20日起施行。在此之前查询现行规定应使用2019年修订版本；地方实施细则仍需按业务所在地核对。',
 'housing-fund-current':'此版本适用至2026年9月19日。2026年9月20日起使用新版；地方缴存、提取与贷款条件需要结合当地实施规定。'
}

def prepare(r):
    snap=json.loads((OUT/(r['id']+'.json')).read_text(encoding='utf-8'))
    assert snap['ok'] and snap['url']==r['url'], r['id']
    assert hashlib.sha256(snap['content'].encode()).hexdigest()==snap['sha256']
    body=snap['content']
    if r['id'] in last:
        m=re.search(r'第一条\s',body)
        assert m, ('first',r['id'])
        body=body[m.start():]
        # Chinese characters are word characters: use explicit punctuation/space instead.
        missing=[n for n in range(1,last[r['id']]+1) if not re.search('第'+han(n)+r'条[\s、，。\u2002\u2003\u3000]',body)]
        assert not missing, ('missing clauses',r['id'],missing)
    else:
        starts={'holiday-2026':'国办发明电','retirement':'为了深入贯彻落实','tax-deductions-2023':'各省、自治区','pension-transfer-2016':'国务院办公厅转发','e-reimbursement':'为适应电子商务','monthly-hours':'各省','infant-tax':'各省'}
        start=starts.get(r['id'])
        assert start and start in body, ('start',r['id'])
        body=body[body.index(start):]
    for tail in ['扫一扫在手机','微信扫一扫','相关链接：','链接：《','联系我们','责任编辑','附件下载:','附件下载：','相关解读','浏览次数：','【打印】']:
        if tail in body: body=body.split(tail)[0]
    # Article 107 is the final provision; omit the website's separately appended amendment excerpt.
    if r['id']=='labor': body=re.split(r'(第一百零七条[^。]*。)',body)[0]+re.search(r'第一百零七条[^。]*。',body)[0]
    if r['id'] in ('tax-deductions-2023','infant-tax'):
        tail='2023年8月28日' if r['id']=='tax-deductions-2023' else '2022年3月19日'
        assert tail in body
        body=body[:body.index(tail)+len(tail)]
    if r['id']=='privacy': body=body[:body.index('第七十四条')]+re.search(r'第七十四条[^。]*。',body)[0]
    if r['id']=='women-rights': body=body[:body.index('第八十六条')]+re.search(r'第八十六条[^。]*。',body)[0]
    if r['id']=='flex-retirement': body=body[:body.index('第十四条')]+re.search(r'第十四条[^。]*。[^。]*。',body)[0]
    # Avoid turning site navigation into source attachments. Preserve actual official downloadable forms.
    attachments=[]
    for a in snap.get('links',[]):
        u=urljoin(r['url'],a['href'])
        if a['title'] and re.search(r'\.(pdf|docx?|xlsx?)(?:$|\?)|/download/',u,re.I) and urlparse(u).hostname.endswith('.gov.cn'):
            attachments.append(a['title']+'：'+u)
    if attachments: body+='\n\n官方附件（未转录附件内容）：\n'+'\n'.join(dict.fromkeys(attachments))
    prefix='适用说明：'+notes[r['id']]+'\n\n' if r['id'] in notes else ''
    source='全国政策原文 · '+ ('国家行政法规库' if 'xzfg.moj.gov.cn' in r['url'] else urlparse(r['url']).hostname)
    result={k:r[k] for k in ['title','category','region','publishedAt','effectiveFrom','effectiveTo']}
    result.update(source=source,sourceUrl=r['url'],updatedAt='2026-09-11',reviewStatus='APPROVED',content=prefix+'【官方正文】\n'+body.strip())
    assert len(result['content'])>200 and r['effectiveFrom']
    return dict(id=r['id'],snapshotSha256=snap['sha256'],bodySha256=hashlib.sha256(result['content'].encode()).hexdigest(),reviewed=r.get('reviewedSha256')==snap['sha256'],record=result)

if __name__=='__main__':
    plans=[];errors=[]
    for row in catalog:
        try: plans.append(prepare(row))
        except Exception as e: errors.append(str(e))
    (OUT/'import-plan.json').write_text(json.dumps(plans,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(dict(prepared=len(plans),errors=errors),ensure_ascii=False,indent=2))
    if errors: raise SystemExit(1)
