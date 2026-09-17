from pathlib import Path
from urllib.parse import urlparse
import json,re
root=Path(__file__).resolve().parents[1];out=root/'.artifacts/reimbursement-review'
catalog=json.loads((root/'n8nwork/knowledge-files/reimbursement-policy-catalog.json').read_text(encoding='utf8'))
notes={
 'data-2025':'财会〔2025〕9号，2025年5月9日印发，网页于5月19日发布。此通知在全国推广电子凭证会计数据标准，是2020年电子凭证报销入账归档规则的配套发展，本次未发现其宣布废止财会〔2020〕6号。对员工而言，请妥善保留电子凭证原文件；单位系统改造与适配由财务部门安排。',
 'rail-2024':'公告2024年第8号，自2024年11月1日起施行；核验时税务总局法规库标注“全文有效”。办理境内铁路客运报销，应取得电子发票（铁路电子客票），可通过铁路12306获取并填写正确的购买方名称和统一社会信用代码。原纸质报销凭证过渡条款对应的是乘车日期在2025年9月30日前的情形，不能直接套用于2026年的新行程。跨境行程需另核对。',
 'air-2024':'公告2024年第9号，自2024年12月1日起施行；核验时税务总局法规库标注“全文有效”。境内航空旅客运输报销，按规定取得电子行程单或其他发票；通过航空企业或代理企业提供的渠道获取。原纸质行程单过渡条款对应的是乘机日期在2025年9月30日前的情形，不能直接套用于2026年的新行程。国际及港澳台行程需另核对。',
 'validation-2026':'资料类型：财政部配套操作指南，页面发布日期2026年2月4日。它帮助单位验证电子凭证及处理环节是否符合会计数据标准，供财务和系统维护人员参考；不是新设员工报销额度或审批人的法律条文。页面未规定独立的法律生效日期，故不填写生效日。',
 'shared-2026':'财会〔2026〕9号，2026年6月23日印发，官网于7月1日发布。这是正式印发稿，不是此前的征求意见稿。适用于有财务共享服务意愿和能力的单位，参照执行。第四条列入费用报销，第九条强调职责、审批权限与档案责任，第十六条涉及报销等系统建设；没有设定全国企业统一报销额度。原文未另列独立生效日期，故不推定生效日。',
 'work-2024':'财会〔2024〕11号，2024年7月26日印发；第五十条明确自2025年1月1日起施行，同时废止财会字〔1996〕17号和财会〔2013〕20号。第十六、十七条涉及审签与审核，第二十五条要求查验、防篡改和防重复入账，第二十七条要求纸质打印件报销时同时保存电子原文件并建立检索关系。此规范没有宣布废止财会〔2020〕6号。'
}
titles={'data-2025':'关于推广应用电子凭证会计数据标准的通知（2025）','rail-2024':'铁路客运全面数字化电子发票公告（2024年第8号）','air-2024':'民航旅客运输全面数字化电子发票公告（2024年第9号）','validation-2026':'电子凭证会计数据标准应用验证系统（2026配套指南）','shared-2026':'管理会计应用指引第804号——财务共享服务（2026正式稿）','work-2024':'会计信息化工作规范（2025年起施行）'}
plan=[]
for row in catalog:
 s=json.loads((out/'sources'/(row['id']+'.json')).read_text(encoding='utf8'));assert s['ok'],row['id']
 if row['id']=='receipt-2020':continue
 t=s['content'];id=row['id']
 if id in ('rail-2024','air-2024'):t=t[t.index('为贯彻落实'):t.index('特此公告。')+len('特此公告。')]
 if id=='shared-2026':t=t[t.index('财会〔2026〕9号'):];t=t[:t.index('第三十条')]+t[t.index('第三十条'):].split('附件下载')[0].strip();assert '本指引由财政部负责解释。' in t
 if id=='work-2024':
  raw=(out/'sources/work-2024.txt').read_text(encoding='utf8');lines=[x.strip() for x in raw.splitlines() if not re.fullmatch(r'\s*\d+\s*',x)]
  body=''
  for line in lines:body+=('\n' if re.match(r'第[一二三四五六七八九十百]+[条章]|（[一二三四五六七八九十]+）',line) else '')+line
  body=re.sub(r'(?<=[\u4e00-\u9fff]) +(?=[\u4e00-\u9fff])','',body);assert '第五十条' in body
  t+='\n\n【正式附件全文 · PDF共13页】\n'+body+'\n\n附件地址：https://www.mof.gov.cn/jrttts/202408/P020240809318550289714.pdf'
 assert not re.search('ICP备|用户登录|热门检索|网站标识码',t),id
 record=dict(category='差旅与报销',title=titles[id],content='【阅读要点】\n'+notes[id]+'\n\n适用边界：公司住宿、交通、餐补金额和审批权限仍按适用的公司制度核对，本文件不直接代替公司标准。\n\n【官网核验】2026-09-11 · '+row['url']+'\n\n【官方'+('指南正文' if id=='validation-2026' else '正文')+'】\n'+t,source=('全国政策配套指南 · ' if id=='validation-2026' else '全国政策原文 · ')+urlparse(row['url']).hostname,region='全国',reviewStatus='APPROVED',publishedAt=row['date'],effectiveFrom=row.get('effective') or (row['date'] if id=='data-2025' else None),effectiveTo=None,sourceUrl=row['url'],jobGrades=None,workTypes=None,legalEntities=None)
 plan.append(dict(key=id,record=record))
before=json.loads((out/'before.json').read_text(encoding='utf8'));a=next(x for x in before if x['id']==1091)
record={k:a[k] for k in plan[0]['record']}
record['content']='【版本核验说明 · 2026-09-11】\n2020是这份文件的实际发文年份，不代表整个报销板块只更新到2020年。本次检索财政部及税务总局官网，未发现财会〔2020〕6号被整体废止的依据，因此保留原文与原日期。请同时查看本板块新补充的《会计信息化工作规范》（2025年起施行）、2025年电子凭证会计数据标准推广通知、铁路及民航电子发票公告、2026年财务共享服务正式指引及配套验证指南。后续文件分别规范凭证、系统或管理流程，不能据此直接替换公司的报销金额和审批人。\n\n'+a['content']
plan.append(dict(key='receipt-2020',id=1091,record=record))
(out/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2),encoding='utf8')
print('Prepared 6 official additions and 1 existing-document version note; original law dates preserved.')
