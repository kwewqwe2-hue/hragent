package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Tenant-scoped extraction. Document text is data, never executable instructions or model prompts. */
public final class SuppliedPolicySearch {
    public static final String CATEGORY="企业提供制度";
    private static final String LEAVE="CHR-RS-17 A0:", TRAVEL="CHR-XZ-24 A0:", FINANCE="CHR-CW-06 B0:";
    private static final String SH_LEAVE="https://www.shanghai.gov.cn/jcsfbrkcqjhfzzh/20230608/7065d882001248d5b2945616c69d3c49.html";
    private static final String ANNUAL="https://rsj.sh.gov.cn/trlzyhshbzbgz_17256/20200617/t0035_1388390.html";
    private static final String CONTRACT="https://www.mohrss.gov.cn/xxgk2020/fdzdgknr/zcfg/fl/202011/t20201102_394622.html";
    private SuppliedPolicySearch() { }
    public static boolean isDocument(PolicyAnswer answer) {
        return answer!=null && Set.of("DOCUMENT_REFERENCE","DOCUMENT_MATCHED","DOCUMENT_REVIEW").contains(answer.status());
    }
    public static boolean eligible(KnowledgeArticle a,Long tenant,LocalDate today) {
        return Objects.equals(tenant,a.getTenantId()) && CATEGORY.equals(a.getCategory())
                && "APPROVED".equals(a.getReviewStatus()) && a.getContent()!=null && a.getSource()!=null
                && (a.getEffectiveFrom()==null||!a.getEffectiveFrom().isAfter(today))
                && (a.getEffectiveTo()==null||!a.getEffectiveTo().isBefore(today));
    }
    public static Optional<PolicyAnswer> answer(List<KnowledgeArticle> all,Long tenant,String query,ProfileView profile,LocalDate today) {
        if(query==null||query.isBlank())return Optional.empty();
        if(!WorkplaceSupport.intent(query).equals("NONE"))return Optional.empty();
        var docs=all.stream().filter(a->eligible(a,tenant,today)).toList();
        if(docs.isEmpty())return Optional.empty();
        String q=normalize(query);
        var keys=new LinkedHashSet<String>();
        String overview=q.replaceAll("[\\p{P}\\s]","");
        switch(overview) {
            case "考勤有哪些", "考勤", "考勤要求" -> keys.addAll(List.of("HANDBOOK-2025:4.1.1",LEAVE+"第三条","HANDBOOK-2025:4.1.3"));
            case "休假有哪些", "休假", "休假政策" -> keys.addAll(List.of(LEAVE+"第十五条",LEAVE+"第六条",LEAVE+"第八条"));
            case "出差有哪些", "出差", "差旅", "差旅有哪些" -> keys.addAll(List.of(TRAVEL+"第四条",TRAVEL+"第十条",TRAVEL+"第十一条",TRAVEL+"第十三条"));
            case "报销注意什么", "报销", "报销有哪些" -> keys.addAll(List.of(FINANCE+"第五条",FINANCE+"第八条",FINANCE+"第九条"));
            case "薪酬福利有哪些", "薪酬福利" -> keys.addAll(List.of("HANDBOOK-2025:3.1.2","HANDBOOK-2025:3.2.1","HANDBOOK-2025:3.2.2"));
            case "入职转正有哪些要求", "入职转正" -> keys.addAll(List.of("HANDBOOK-2025:2.1.3","HANDBOOK-2025:2.1.4"));
            default -> { }
        }
        boolean broadQuestion=!keys.isEmpty();
        boolean financial=has(q,"支付","借款","备用金","押金","保证金","固定资产","易耗品","装修","代发","采购","接待费","办公费","会务费","咨询费","付汇","购汇","分利")
                || (has(q,"报销")&&has(q,"谁","审批","权限","流程"));
        if(broadQuestion) { /* Complete overview clauses were selected above. */ }
        else if(has(q,"住宿","酒店","旺季")) {
            keys.add(TRAVEL+"第十条");
            if(has(q,"旺季","上浮","季节","三亚","青岛","哈尔滨","洛阳","海口","张家口","秦皇岛","西宁","桂林","北海","拉萨","海南州","大连","承德","海拉尔","满洲里","阿尔山","二连浩特","额济纳","吉林","延边","长白山","牡丹江","伊春","大兴安岭","黑河","佳木斯","烟台","威海","日照","文昌","澄迈","琼海","万宁","陵水","保亭","玉树","海北","黄南","海东","海西"))keys.add(TRAVEL+"SEASONS");
        } else if(has(q,"出差","差旅")&&has(q,"补办","补填","补签","回来","返回","报销期限","多久报销","几天报销")) {
            keys.add(TRAVEL+"第七条");keys.add(TRAVEL+"第十三条");
        } else if(has(q,"飞机","机票","高铁","动车","升舱","商务舱","经济舱","一等座","二等座","出差交通"))keys.add(TRAVEL+"第九条");
        else if(has(q,"餐费","餐饮","吃饭","伙食")&&!financial){keys.add(TRAVEL+"第十一条");keys.add(TRAVEL+"第十二条");}
        else if(has(q,"出差","差旅")&&has(q,"审批","申请","紧急")&&!financial){keys.add(TRAVEL+"第四条");if(has(q,"紧急"))keys.add(TRAVEL+"第五条");}
        else if(has(q,"年休假"))keys.add(LEAVE+"第六条");
        else if(has(q,"事假"))keys.add(LEAVE+"第八条");
        else if(has(q,"婚假"))keys.add(LEAVE+"第九条");
        else if(has(q,"丧假","岳父母","公婆","祖父母","外祖父母"))keys.add(LEAVE+"第十条");
        else if(has(q,"育儿假"))keys.add(LEAVE+"第十二条");
        else if(has(q,"陪产假","献血"))keys.add(LEAVE+"第十四条");
        else if(has(q,"产假","产检","产前假","生育假","哺乳","流产"))keys.add(LEAVE+"第十一条");
        else if(has(q,"医疗期"))keys.add(LEAVE+"第七条");
        else if(has(q,"病假")) {keys.add("HANDBOOK-2025:4.2.2.4");keys.add(LEAVE+"第七条");}
        else if(has(q,"试用","转正","见习期"))keys.add("HANDBOOK-2025:2.1.4");
        else if(has(q,"培训")&&has(q,"协议","费","服务期","返还"))keys.add("HANDBOOK-2025:5.3");
        else if(has(q,"发放","几号发","发工资","发薪日")&&has(q,"工资","薪"))keys.add("HANDBOOK-2025:3.1.3");
        else if(has(q,"入职")&&has(q,"材料","手续","带什么","证件"))keys.add("HANDBOOK-2025:2.1.3");
        else if(has(q,"绩效")&&has(q,"申诉","异议","不同意"))keys.add("HANDBOOK-2025:7.7");
        else if(has(q,"加班")){keys.add(LEAVE+"第四条");keys.add("HANDBOOK-2025:4.4");}
        else if(has(q,"工作时间","几点上班","几点下班","工时"))keys.add(LEAVE+"第三条");
        else if(has(q,"请假")&&!has(q,"病假")){keys.add(LEAVE+"第十五条");keys.add("HANDBOOK-2025:4.2.1");}
        if(financial&&keys.isEmpty()) {
            var table=rank(docs.stream().filter(a->a.getSource().contains(FINANCE+"T")).toList(),q);
            if(!table.isEmpty())keys.add(key(table.getFirst()));
        }
        var matches=new ArrayList<KnowledgeArticle>();
        for(String k:keys)docs.stream().filter(a->key(a).equals(k)).forEach(matches::add);
        if(matches.isEmpty())matches.addAll(rank(docs,q).stream().limit(2).toList());
        if(matches.isEmpty())return Optional.empty();
        boolean conflict=matches.stream().anyMatch(a->matches.stream().anyMatch(b->!Objects.equals(a.getId(),b.getId())&&a.getTitle().equals(b.getTitle())&&!a.getContent().equals(b.getContent())));
        boolean applicable=matches.stream().allMatch(a->PolicyCopilotService.applicable(a,profile,today))
                && profile.workType()!=null&&!profile.workType().isBlank()
                && !has(profile.workType(),"外包","派遣","返聘","实习","见习","聘用","临时劳务");
        var gaps=new ArrayList<String>();
        if(!applicable)gaps.add("以下是本企业知识库中所提供文件的内容。你的合同主体、用工身份或版本日期尚未全部匹配，不能直接认定为你的个人执行标准；可先按条款了解，再请 HR 核对适用范围。");
        if(conflict)gaps.add("同一条款出现不同内容，需 HR 核实版本，暂不确定执行标准。");
        var citations=matches.stream().map(SuppliedPolicySearch::citation).toList();
        StringBuilder reply=new StringBuilder(conflict?"这条制度需要先核对版本。":applicable?"根据与你档案匹配的文件，相关规定如下。":"根据你们提供的制度文件，相关内容如下。");
        if(broadQuestion) {
            if(has(q,"休假"))reply.append("\n你可以先了解请假手续、年假和事假。婚假、病假、生育假、育儿假等各有条件，也可以继续问具体假种。");
            if(has(q,"报销"))reply.append("\n先完成业务事前审批，再核对预算、发票凭证和附件，最后按费用类别及金额走支付审批。出差、采购、接待等权限不同；你可以告诉我费用类型和金额，再查询对应流程。");
        }
        for(var a:matches) {
            String summary=field(a.getContent(),"问答要点");
            reply.append("\n\n").append(a.getTitle()).append("\n").append(summary.isBlank()?field(a.getContent(),"条款原文"):summary);
            String note=field(a.getContent(),"核对提示");if(!note.isBlank())reply.append("\n说明：").append(note);
            reply.append("\n出处：").append(a.getSource()).append("；文件生效日期：").append(a.getEffectiveFrom()==null?"原文未载明（不以版本年份代替）":a.getEffectiveFrom());
        }
        String law=legalNote(q);
        if(!law.isBlank())reply.append("\n\n法律依据与核对提示（核验于2026-09-08）：\n").append(law);
        if(has(q,"育儿假","婚假","培训协议","培训费","未休","过期","清零","作废")||has(q,"请假")&&keys.contains("HANDBOOK-2025:4.2.1"))
            gaps.add("回答已区分文件条款与法规补充；存在差异时不能仅凭内部文字减损法定权益。");
        if(!applicable)reply.append("\n\n").append(gaps.getFirst());
        return Optional.of(new PolicyAnswer(reply.toString(),conflict?"DOCUMENT_REVIEW":applicable?"DOCUMENT_MATCHED":"DOCUMENT_REFERENCE",profile,citations,gaps));
    }
    private record Scored(KnowledgeArticle article,double score) { }
    private static List<KnowledgeArticle> rank(List<KnowledgeArticle> docs,String q) {
        String focused=q.replaceAll("谁|审批|权限|流程|申请|报销|员工|公司|标准|几天|一天|天数|多少|什么","");
        Set<String> grams=grams(focused);var scores=new ArrayList<Scored>();
        for(var a:docs){
            String title=normalize(a.getTitle().replaceFirst("^.*?｜",""));String body=normalize(field(a.getContent(),"条款原文"));
            int titleHits=0,hits=0;double score=0;
            for(String term:grams){boolean t=title.contains(term);if(t)titleHits++;if(t||body.contains(term)){hits++;score+=t?6:1;}}
            if(titleHits==0)continue;
            if(a.getSource().contains("CHR-RS-17"))score+=2;
            scores.add(new Scored(a,score/Math.pow(Math.max(1,body.length()/600.0),.2)));
        }
        scores.sort(Comparator.comparingDouble(Scored::score).reversed());
        if(scores.isEmpty())return List.of();
        double cutoff=Math.max(6,scores.getFirst().score()*.75);
        return scores.stream().filter(s->s.score()>=cutoff).map(Scored::article).toList();
    }
    static String normalize(String q){return q.toLowerCase(Locale.ROOT).replaceAll("\\s+","")
        .replace("年假","年休假").replace("薪资","工资").replace("发薪日","工资发放日期").replace("发工资","工资发放")
        .replace("酒店","住宿").replace("宾馆","住宿").replace("住宿费","住宿").replace("打车费","市内交通费")
        .replace("报账","报销").replace("试用多久","试用期").replace("请问","").replace("怎么","").replace("如何","")
        .replace("规定","").replace("制度","").replace("可以","").replace("多少","").replace("需要","");}
    private static Set<String> grams(String q){var result=new HashSet<String>();var m=Pattern.compile("[\\p{IsHan}a-z]+").matcher(q);while(m.find()){String word=m.group();for(int i=0;i<word.length()-1;i++)result.add(word.substring(i,i+2));}return result;}
    private static boolean has(String q,String...terms){return Arrays.stream(terms).anyMatch(q::contains);}
    private static String key(KnowledgeArticle a){String s=field(a.getContent(),"检索词");int end=s.indexOf(' ',s.indexOf(':')+1);return end<0?s:s.substring(0,end);}
    public static String field(String content,String label){var m=Pattern.compile("(?s)(?:^|\\n\\n)"+Pattern.quote(label)+"：(.*?)(?=\\n\\n(?:适用范围|问答要点|条款原文|核对提示|检索词)：|$)").matcher(content);return m.find()?m.group(1).strip():"";}
    public static Citation citation(KnowledgeArticle a){return new Citation(a.getId(),a.getTitle(),field(a.getContent(),"条款原文"),a.getSource(),a.getSourceUrl(),a.getPublishedAt(),a.getEffectiveFrom(),a.getEffectiveTo(),a.getRegion(),a.getJobGrades(),field(a.getContent(),"适用范围"),a.getLegalEntities());}
    private static String legalNote(String q){
        if(has(q,"育儿假"))return "上海规定按子女出生日起算周期年，未满3周岁时父母双方每年各享5天，按符合条件的子女数量累计；一般在该周期年内使用。手册提到按年度使用，不能直接理解为一律自然年12月31日清零。依据：沪府规〔2022〕18号第三条（有效至2027-10-31）。\n"+SH_LEAVE;
        if(has(q,"婚假"))return "休假办法写连续10个公历日、登记后12个月内用完；上海规定增加的婚假遇法定节假日顺延。因此不能仅按10个自然日截断，实际起止日期应结合节假日核对。依据：沪府规〔2022〕18号第二条（有效至2027-10-31）。\n"+SH_LEAVE;
        if(has(q,"年休假")&&has(q,"未休","过期","清零","作废","跨年","有效","到期","失效"))return "文件中的安排截止日不等于自动丧失法定未休假报酬。因工作原因未安排、经同意少安排，与员工本人书面提出不休是不同情形，应分别核对。依据：《企业职工带薪年休假实施办法》第九、十条。\n"+ANNUAL;
        if(has(q,"培训")&&has(q,"费","协议","离职","服务期"))return "手册的五年返还及逐年递减安排不能套用于所有培训。须核对是否为有专项费用的专业技术培训、有效服务期协议及费用凭证；违约金受实际培训费用和尚未履行服务期分摊金额限制。依据：《劳动合同法》第二十二、二十五条。\n"+CONTRACT;
        if(has(q,"试用期","解除","辞退","扣奖","扣工资","赔偿","保密","竞业"))return "上述为企业文件条款，不能据此直接判定某次解除、扣款或违约责任合法。涉及个案应结合劳动合同、实际事实、法定条件与程序，由 HR/ER 核实；试用期还须核对合同期限、是否曾约定及法定上限。\n《劳动合同法》："+CONTRACT;
        if(has(q,"病假")&&has(q,"工资","待遇","钱怎么"))return "手册列出了按连续工龄60%—100%的病假工资档位；上海官方解读还区分连续休假是否超过6个月，并设最低保障。不能只用手册比例计算最终到手金额，需核对休假时长、工资基数、工龄及当期适用标准。\n上海人社《病假工资具体怎么算？》：https://rsj.sh.gov.cn/tmsztc_17502/20251009/t0035_1435943.html";
        if(has(q,"加班"))return "标准工时下，工作日延时按不低于150%支付；休息日不能安排补休的按不低于200%支付；法定节假日按不低于300%支付。手册的等时调休不能替代所有类型加班工资，特殊工时另核对批准情况。\n上海人社《实行标准工时制的加班工资支付标准是什么？》：https://rsj.sh.gov.cn/txcgl_17550/20210508/t0035_1398838.html";
        return "";
    }
}
