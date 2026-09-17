"""Build a local, page-attributed corpus from the four supplied PDFs.
Run with --source-dir; PyMuPDF is required. No documents are sent to a model.
"""
import argparse, hashlib, json, re, sys
from pathlib import Path
sys.path.insert(0, str(Path('.artifacts/pdf-tools').resolve()))
import pymupdf

parser = argparse.ArgumentParser()
parser.add_argument('--source-dir', required=True)
args = parser.parse_args()
root = Path(args.source_dir)
out = Path('n8nwork/knowledge-files/company-policies-2025.json')
names = ['员工手册2025版(1).pdf','CHR-RS-17 A0 职工工时及休假实施办法(1).pdf','CHR-XZ-24 A0 员工出差管理规定(1).pdf','CHR-CW-06 B0 关于财务支付审批权限的规定.pdf']
titles = ['员工手册（2025版）','职工工时及休假实施办法','员工出差管理规定','关于财务支付审批权限的规定']
codes = ['HANDBOOK-2025','CHR-RS-17 A0','CHR-XZ-24 A0','CHR-CW-06 B0']
scopes = ['上海临港漕河泾人才有限公司、上海临港人才有限公司全体在职人员，包括全日制与非全日制劳动合同员工、退休返聘、实习及见习；外包项目服务和临时劳务人员不适用。',
          '与上海临港漕河泾人才有限公司依法建立劳动关系的职工；不包括退休返聘、实习见习、劳务派遣、聘用及外包人员。',
          '上海临港漕河泾人才有限公司各中心、子公司。中层干部指任用岗位职级为中心副总监及以上人员。',
          '上海临港漕河泾人才有限公司本部各部门及上海临港人才有限公司；临港人才与本部各部门同级别管理。']
docs=[]; chunks=[]
def clean(page):
    return '\n'.join(re.sub(r'[ \t]+','',line.strip()) for line in page.splitlines()
                     if line.strip() and not re.match(r'^(编号：|\d+$)',line.strip()))
def add(d,key,title,body,pages,keywords='',summary='',note=''):
    chunks.append(dict(key=f'{codes[d]}:{key}',document=codes[d],title=title,body=body.strip(),pages=sorted(set(pages)),keywords=keywords,summary=summary,note=note,scope=scopes[d]))
for d,name in enumerate(names):
    raw=(root/name).read_bytes(); pdf=pymupdf.open(stream=raw,filetype='pdf')
    pages=[clean(p.get_text(sort=True)) for p in pdf]
    docs.append(dict(code=codes[d],title=titles[d],fileName=name,sha256=hashlib.sha256(raw).hexdigest(),pages=len(pdf),effectiveFrom=None if d==0 else '2025-10-14',scope=scopes[d],rawPages=pages))
    if d==0:
        current=None
        for p,body in enumerate(pages[2:32],3):
            for line in body.splitlines():
                match=re.match(r'^(\d+\.\d+(?:\.\d+)*)([^\d.].*)$',line)
                if match:
                    if current and current['body']: add(0,**current)
                    current=dict(key=match[1],title=match[2],body='',pages=[])
                elif current:
                    # Parent chapter headings are separators, not continuation text.
                    if re.match(r'^\d+[人薪考培日绩奖企]',line): continue
                    current['body']+=line+'\n';current['pages'].append(p)
        if current and current['body']:add(0,**current)
    else:
        current=None
        end={1:6,2:4,3:3}[d]
        for p,body in enumerate(pages[1:end],2):
            for line in body.splitlines():
                m=re.match(r'^(第[一二三四五六七八九十]+条)(.*)$',line)
                if m:
                    if current: add(d,**current)
                    current=dict(key=m[1],title=m[1]+m[2] if d!=1 else m[1]+m[2],body=line+'\n',pages=[p])
                elif current and not re.match(r'^第[一二三四五六七八九十]+章',line):
                    current['body']+=line+'\n';current['pages'].append(p)
        if current:add(d,**current)

# All financial table rows below were checked against the PDF page images.
holiday=next(c for c in chunks if c['key']=='HANDBOOK-2025:4.2.2')
parts=list(re.finditer(r'(?m)^([1-5])、([^\n]+)',holiday['body']))
for i,m in enumerate(parts):
    text=holiday['body'][m.start():parts[i+1].start() if i+1<len(parts) else len(holiday['body'])]
    add(0,'4.2.2.'+m[1],m[2],text,{'1':[16],'2':[16,17],'3':[17],'4':[17,18],'5':[18,19]}[m[1]])
# Keep the original parent available in the source archive; retrieve its smaller complete subsections.
chunks.remove(holiday)
B='业务中心负责人 → 财务部经理 → 总经理助理'
A='各部门负责人 → 财务部经理 → 总经理助理'
H='行政人事中心负责人 → 财务部经理 → 总经理助理'
S='行政人事中心负责人/品牌运维中心负责人 → 财务部经理 → 总经理助理'
def table(key,title,page,rows,keywords=''):
    body='\n'.join(f'• {condition}：审批 {chain}；最终审批 {final}。' for condition,chain,final in rows)
    add(3,key,title,body,[page],keywords,note='这是支付环节的权限，不代替业务事前审批。所有支付须在预算内；超预算先追加预算。总经理助理仅复审分管领域，未设置该岗位分管的部门跳过此节点；财务共享系统以实际授权为准（第三章第七条、第四章第八至十四条，PDF第2—3页）。')
table('T01','五险一金等代办支付审批',4,[('200万元（含）以下',B,'副总经理'),('200万元以上',B+' → 副总经理','总经理')],'200万 社保代办')
table('T02','人事管理其他代办支付审批',4,[('10万元（含）以下',B,'副总经理'),('10万元以上',B+' → 副总经理','总经理')])
table('T03','互代业务款、关联方预付款、体外互代服务费',4,[('支付互代业务款、关联方互代款项预付',B,'副总经理'),('体外互代服务费10万元（含）以下',B,'副总经理'),('体外互代服务费10万元以上',B+' → 副总经理','总经理')])
table('T04','代发薪酬及雇员银行卡制作费',4,[('代发薪酬（含退回重发）、雇员银行卡制作费20万元（含）以下','业务中心负责人','财务部经理'),('上述业务20万元以上','业务中心负责人 → 财务部经理','总经理助理')],'代发工资 20万')
table('T05','代为支付报销款、代缴个税',4,[('代为支付报销款、代缴个税',B,'副总经理')])
table('T06','MBP、体检保健、弹性福利产品成本',4,[('MBP产品成本（普通团险、高端医疗险、雇主责任险等），雇员入职/年度体检福利和外地体检报销，弹性福利成本及服务费',B,'副总经理')])
table('T07','雇员权益：代教费、住院保障、解除合约补偿、诉讼赔偿',5,[('商社见习生代教费、退休雇员总工会住院保障',B,'副总经理'),('下岗雇员解除合约补偿：客户承担（速创推送）',B,'副总经理'),('下岗雇员解除合约补偿：计入业务成本（非速创）',B+' → 财务负责人 → 副总经理','总经理'),('诉讼赔偿5万元（含）以下',B,'副总经理'),('诉讼赔偿5万元以上至10万元（含）',B+' → 财务负责人 → 副总经理','总经理'),('诉讼赔偿10万元以上',B+' → 财务负责人 → 副总经理 → 总经理','董事长')])
table('T08','招聘、培训业务成本支付审批',5,[('招聘业务成本、培训业务成本',B,'副总经理')])
table('T09','对外投资、数字化立项项目支付审批',5,[('对外投资项目',B+' → 财务负责人 → 副总经理 → 总经理','董事长'),('数字化立项项目','品牌运维中心负责人 → 财务部经理 → 总经理助理 → 财务负责人 → 副总经理 → 总经理','董事长')])
table('T10','装修工程合同金额支付审批',5,[('合同金额1万元（含）以下','行政人事中心负责人 → 财务部经理','总经理助理'),('合同金额1万元以上至5万元（含）',H,'副总经理'),('合同金额5万元以上至20万元（含）',H+' → 财务负责人 → 副总经理','总经理'),('合同金额20万元以上',H+' → 财务负责人 → 副总经理 → 总经理','董事长')])
table('T11','固定资产采购、购置车辆审批',6,[('固定资产5万元（含）以下',S,'副总经理'),('固定资产合同金额5万元以上至10万元（含）',S+' → 财务负责人 → 副总经理','总经理'),('固定资产10万元以上',S+' → 财务负责人 → 副总经理 → 总经理','董事长'),('购置车辆',H+' → 财务负责人 → 副总经理 → 总经理','董事长')])
table('T12','低值易耗品采购审批',6,[('5000元（含）以下',S,'副总经理'),('5000元以上',S+' → 副总经理','总经理')])
table('T13','租赁费、物业费及租房押金',6,[('先办理租赁合同审批；租赁费、物业费及租房押金','行政人事中心负责人/品牌运维中心负责人 → 财务部经理','总经理助理')])
table('T14','离沪出差、市内交通费支付审批',6,[('离沪出差费用',A+' → 副总经理','总经理'),('市内交通费500元（含）以下',A,'副总经理'),('市内交通费500元以上',A+' → 副总经理','总经理')],'差旅报销 出差报销 打车费 车费')
table('T15','营销、接待费报销审批',6,[('1000元（含）以下','各部门负责人 → 财务部经理','总经理助理'),('1000元以上至3000元（含）',A,'副总经理'),('3000元以上',A+' → 财务负责人 → 副总经理','总经理')],'业务接待 餐饮招待')
table('T16','总经理报销费用',7,[('总经理报销的费用','董事长','原表合并单元格，仅列董事长')])
table('T17','礼品采购审批',7,[('礼品采购',A+' → 财务负责人 → 副总经理','总经理')])
table('T18','广告宣传、市场调研、营销会务费及咨询费',7,[('广告宣传、市场调研、营销会务费：2万元（含）以下',A,'副总经理'),('广告宣传、市场调研、营销会务费：2万元以上',A+' → 财务负责人 → 副总经理','总经理'),('咨询费',A+' → 财务负责人 → 副总经理','总经理')])
table('T19','员工借款、备用金、押金、保证金',7,[('员工借款2000元（含）以下','各部门负责人 → 财务部经理','总经理助理'),('员工借款2000元以上至1万元（含）',A,'副总经理'),('员工借款1万元以上',A+' → 财务负责人 → 副总经理','总经理'),('备用金、押金、保证金（不含租赁押金）',A+' → 副总经理','总经理')])
table('T20','消耗性办公费审批',8,[('3000元（含）以下','各部门负责人 → 财务部经理','总经理助理'),('3000元以上至2万元（含）',A,'副总经理'),('2万元以上',A+' → 财务负责人 → 副总经理','总经理')],'办公用品 办公耗材')
table('T21','行政人事中心薪酬报账单',8,[('职工工资奖金、现金福利、社保公积金年金保险个税、其他从业人员人工支出、工会经费、职工培训支出、劳防用品','行政人事中心负责人 → 财务部经理 → 副总经理','总经理')])
table('T22','行政人事中心捐赠、协会费、出访及公司会务',8,[('捐赠、各类协会费',H+' → 财务负责人 → 副总经理','总经理'),('出访费用、公司会务活动',H+' → 财务负责人 → 副总经理 → 总经理','董事长')])
table('T23','车辆维修费、车辆及财产保险费',8,[('5000元（含）以下','行政人事中心负责人 → 财务部经理','总经理助理'),('5000元以上至2万元（含）',H,'副总经理'),('2万元以上',H+' → 财务负责人 → 副总经理','总经理')])
table('T24','财务结算中心购汇付汇、税费、审计评估、分利、内部划转',9,[('购汇、付汇（境外业务用）；相关税费（除个税）；内部资金划转','财务部经理','原表为合并单元格，仅列财务部经理，不额外推断审批人'),('审计、评估费','财务部经理','总经理'),('付汇（其他用途）','财务部经理','副总经理'),('分利','财务部经理 → 财务负责人 → 副总经理','总经理')])
table('T25','权限表未涵盖的其他支付',9,[('上述表格未涵盖的业务类、非业务类支付',A+' → 财务负责人 → 副总经理','总经理')])
add(3,'FORM','支付审批授权书',docs[3]['rawPages'][9],[10],'授权 被授权人 身份证 期限')
add(2,'FORM','出差审批表填写项目',docs[2]['rawPages'][4],[5],'表单 预算 申请单位 费用承担单位')
seasons='张家口市：7—9月、11月至次年3月；秦皇岛市：7—8月；承德市：7—9月；海拉尔市、满洲里市、阿尔山市、二连浩特市：7—9月；额济纳旗：9—10月；大连：7—9月；吉林市、延边州、长白山管理区：7—9月；哈尔滨市：7—9月；牡丹江市、伊春市、大兴安岭地区、黑河市、佳木斯市：6—8月；青岛市、烟台市、威海市、日照市：7—9月；洛阳市：4月至5月上旬；桂林市、北海市：1—2月、7—9月；海口市、文昌市、澄迈县：11月至次年2月；琼海市、万宁市、陵水县、保亭县：11月至次年3月；三亚市：10月至次年4月；拉萨市及西藏其他地区：6—9月；西宁市：6—9月；玉树州、海北州、黄南州、海东市、海南州、海西州：5—9月。'
add(2,'SEASONS','附件二：住宿旺季城市与月份',seasons,[6],'旺季 上浮 季节 三亚 海口 洛阳 青岛 哈尔滨 张家口',note='须同时符合出差城市与旺季日期；旺季限额最多上浮20%，不是固定发放补贴。海南省和青海省海南州不能混淆；洛阳5月应具体到日期。')

# Readable labels improve retrieval without adding facts to original clauses.
labels={1:{'第一条':'目的与依据','第二条':'适用人员与排除范围','第三条':'工作时间与特殊工时','第四条':'加班申请及工资','第五条':'法定节假日、妇女节和青年节','第六条':'年假天数、有效期与半天单位','第七条':'病假和医疗期','第八条':'事假天数、工资与审批','第九条':'婚假天数与使用期限','第十条':'丧假亲属范围与天数','第十一条':'产检、产前、产假、生育假、哺乳与流产假','第十二条':'育儿假','第十三条':'工伤假与停工留薪期','第十四条':'献血假与陪产假','第十五条':'请假手续与材料','第十六条':'考勤及休假违规处理','第十七条':'法律法规优先','第十八条':'实施与生效','第十九条':'解释部门'},2:{'第一条':'出差管理目的','第二条':'出差制度适用范围','第三条':'中层干部的职级定义','第四条':'出差事前审批权限','第五条':'紧急出差审批','第六条':'急病或不可抗力延长滞留','第七条':'出差补办审批期限','第八条':'市内培训或会议住宿','第九条':'出差交通工具、机票舱位和升舱','第十条':'出差酒店住宿限额标准','第十一条':'出差餐费和业务接待','第十二条':'统一安排交通餐饮不得重复报销','第十三条':'出差报销期限','第十四条':'出差解释部门','第十五条':'出差新旧制度顺序'}}
for c in chunks:
    d=codes.index(c['document']); key=c['key'].split(':',1)[1]
    if d in labels:c['title']=key+' '+labels[d].get(key,c['title'])
    if d==3 and not key.startswith('T') and key!='FORM':
        c['title']=c['body'].split('\n')[0][:65]
    if d==0:
        c['note']='手册仅标注2025版，未载明精确生效日期。第1.3节说明与公司最新规章制度不一致时，以公司规章制度为准。'
    if d==1 and key=='第六条': c['summary']='累计工龄满1年不满10年：5天；满10年不满20年：10天；满20年：15天。年假使用到次年3月31日，最小单位半天，法定节假日及休息日不计入；累计病假达到本条门槛时当年不享受。这里是年度档位，不是个人实时余额。'
    if d==1 and key=='第八条': c['summary']='事假不计发工资；一个自然年度累计不超过22个工作日，个人累计不超过180个工作日。3天以内由部门负责人批准，超过3天需分管领导批准。'
    if d==2 and key=='第十条': c['summary']='北京、上海、广州、深圳：中层干部每人每晚最高950元，员工900元；其他城市：中层干部750元，员工700元。限额内按实际住宿费用报销，并非定额补贴。附件二指定城市在旺季最多上浮20%；因公超限须说明原因并获批。确认你的标准需要目的地、住宿日期及是否为中心副总监及以上。'
    if d==2 and key=='第四条': c['summary']='出差前填写出差审批表：中层干部（含带队）由总经理审批，员工由分管领导审批。部门负责人及以上还须说明事由及授权安排。费用支付审批另看财务权限表。'
    if d==2 and key=='第十一条': c['summary']='员工自己的餐费凭发票实报实销，每人每天一般不超过150元；业务接待须事前审批，另按业务接待管理规定，不能把150元当作接待标准。'
    if d==1 and key in ['第八条','第十五条']:
        c['note']='手册第4.2.1节（PDF第15—16页）的通用请假审批层级更细；本办法对事假另有专项权限。请按假种区分，钉钉实际流程若不一致，请行政人事中心核对，不能把一套权限套到所有假种。'
    c['title']=f"{titles[d]}｜{c['title']}"
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(dict(verifiedAt='2026-09-08',documents=docs,chunks=chunks),ensure_ascii=False,indent=2),encoding='utf-8')
fixture=Path('hragentv1/backend/src/test/resources/supplied-policies.json')
fixture.parent.mkdir(parents=True,exist_ok=True)
fixture.write_bytes(out.read_bytes())
print(json.dumps({'documents':len(docs),'pages':sum(d['pages'] for d in docs),'sections':len(chunks),'output':str(out)},ensure_ascii=True))
