package com.hragent.hragentv1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hragent.hragentv1.domain.KnowledgeArticle;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SuppliedPolicySearchTest {
    private static final LocalDate TODAY=LocalDate.of(2026,9,8);
    private static List<KnowledgeArticle> docs;
    private static ProfileView unknown(){return new ProfileView(1L,"E001","员工","北京",null,null,null,null,null,null,null,null,List.of("合同主体","工种"));}
    @BeforeAll static void load() throws Exception {
        var input=SuppliedPolicySearchTest.class.getResourceAsStream("/supplied-policies.json");
        Assumptions.assumeTrue(input!=null,"Private PDF fixture is generated locally by scripts/build-company-policies.py; it is not committed.");
        var root=new ObjectMapper().readTree(input);
        docs=new ArrayList<>();long id=1;
        for(var c:root.get("chunks")){
            var a=new KnowledgeArticle();a.setId(id++);a.setTenantId(1L);a.setCategory(SuppliedPolicySearch.CATEGORY);a.setTitle(c.get("title").asText());
            String key=c.get("key").asText();a.setSource("文件 · PDF第"+c.get("pages")+"页 · "+key);
            a.setContent("适用范围："+c.get("scope").asText()+"\n\n问答要点："+c.get("summary").asText()+"\n\n条款原文："+c.get("body").asText()+"\n\n核对提示："+c.get("note").asText()+"\n\n检索词："+key+" "+c.get("keywords").asText());
            a.setLegalEntities("上海临港漕河泾人才有限公司");if(!key.startsWith("HANDBOOK"))a.setEffectiveFrom(LocalDate.of(2025,10,14));docs.add(a);
        }
    }
    private PolicyAnswer ask(String q){return SuppliedPolicySearch.answer(docs,1L,q,unknown(),TODAY).orElseThrow();}
    @ParameterizedTest @CsvSource(delimiter='|',value={
        "考勤有哪些规定？|HANDBOOK-2025:4.1.1|考勤",
        "休假有哪些规定？|CHR-RS-17 A0:第十五条|假种",
        "出差有哪些规定？|CHR-XZ-24 A0:第四条|出差前",
        "报销需要注意什么？|CHR-CW-06 B0:第九条|费用类型和金额",
        "薪酬福利有哪些规定？|HANDBOOK-2025:3.1.2|固定收入",
        "入职转正有哪些要求？|HANDBOOK-2025:2.1.4|转正申请",
        "年假有效期到什么时候|CHR-RS-17 A0:第六条|次年3月31日",
        "年假最小单位|CHR-RS-17 A0:第六条|半天",
        "工龄10年年假几天|CHR-RS-17 A0:第六条|10天",
        "员工去北京出差住宿标准|CHR-XZ-24 A0:第十条|900元",
        "普通员工到杭州住宿多少|CHR-XZ-24 A0:第十条|700元",
        "三亚12月旺季酒店上浮多少|CHR-XZ-24 A0:SEASONS|10月至次年4月",
        "出差餐费一天多少|CHR-XZ-24 A0:第十一条|150元",
        "机票经济舱售罄能升舱吗|CHR-XZ-24 A0:第九条|事前报批",
        "出差返回后几天报销|CHR-XZ-24 A0:第十三条|一周内",
        "出差补办审批几天|CHR-XZ-24 A0:第七条|三个工作日",
        "普通员工出差谁审批|CHR-XZ-24 A0:第四条|分管领导",
        "离沪出差报销谁审批|CHR-CW-06 B0:T14|最终审批 总经理",
        "员工借款2000元谁审批|CHR-CW-06 B0:T19|2000元（含）以下",
        "低值易耗品5000元审批|CHR-CW-06 B0:T12|5000元（含）以下",
        "固定资产10万元谁审批|CHR-CW-06 B0:T11|10万元（含）",
        "市内交通费500元谁审批|CHR-CW-06 B0:T14|500元（含）以下",
        "装修合同20万元谁审批|CHR-CW-06 B0:T10|20万元（含）",
        "代发薪酬20万元谁审批|CHR-CW-06 B0:T04|财务部经理",
        "事假3天谁审批|CHR-RS-17 A0:第八条|3天以内",
        "婚假几天|CHR-RS-17 A0:第九条|法定节假日顺延",
        "育儿假自然年还是生日计算|CHR-RS-17 A0:第十二条|周期年",
        "陪产假几天|CHR-RS-17 A0:第十四条|10天",
        "医疗期多长|CHR-RS-17 A0:第七条|24个月",
        "试用期多久|HANDBOOK-2025:2.1.4|六个月",
        "工资几号发放|HANDBOOK-2025:3.1.3|每月15日",
        "入职需要什么材料|HANDBOOK-2025:2.1.3|照片2张",
        "病假要什么材料|HANDBOOK-2025:4.2.2.4|二级及以上医院",
        "培训费用离职必须返还吗|HANDBOOK-2025:5.3|不能套用于所有培训",
        "绩效考核异议申诉期限|HANDBOOK-2025:7.7|5个工作日"
    }) void retrievesPreciseCompleteClause(String query,String source,String fact){
        var result=ask(query);assertThat(result.citations()).anyMatch(c->c.source().contains(source));assertThat(result.answer()).contains(fact);
        assertThat(result.status()).isEqualTo("DOCUMENT_REFERENCE");assertThat(result.answer()).contains("不能直接认定");
    }
    @Test void missingDateIsNotInvented(){var r=ask("工资几号发放");assertThat(r.citations().getFirst().effectiveFrom()).isNull();assertThat(r.answer()).contains("原文未载明");}
    @Test void unrelatedQuestionsDoNotReturnRandomApprovalRows(){assertThat(SuppliedPolicySearch.answer(docs,1L,"量子补贴由谁审批",unknown(),TODAY)).isEmpty();}
    @Test void tenantCannotReadAnotherTenantsDocuments(){assertThat(SuppliedPolicySearch.answer(docs,2L,"出差住宿",unknown(),TODAY)).isEmpty();}
    @Test void futureExpiredAndDraftDocumentsCannotBeUsed(){
        var a=new KnowledgeArticle();a.setTenantId(1L);a.setCategory(SuppliedPolicySearch.CATEGORY);a.setContent("x");a.setSource("x");
        a.setReviewStatus("DRAFT");assertThat(SuppliedPolicySearch.eligible(a,1L,TODAY)).isFalse();a.setReviewStatus("APPROVED");
        a.setEffectiveFrom(TODAY.plusDays(1));assertThat(SuppliedPolicySearch.eligible(a,1L,TODAY)).isFalse();
        a.setEffectiveFrom(TODAY);a.setEffectiveTo(TODAY.minusDays(1));assertThat(SuppliedPolicySearch.eligible(a,1L,TODAY)).isFalse();
    }
    @Test void annualDeadlineDoesNotMeanAutomaticLossOfCompensation(){assertThat(ask("年假过期自动作废吗").answer()).contains("不等于自动丧失","书面提出不休");}
    @Test void citySeasonsAndAmountBandsAreNotFlattened(){var r=ask("洛阳5月住宿旺季");assertThat(r.answer()).contains("5月上旬","最多上浮20%","不是固定发放补贴");}
    @Test void policyAppealDeadlineIsInformationalButPersonalReportStillEscalates(){
        assertThat(ErSafety.informational("绩效考核申诉期限是多久")).isTrue();
        assertThat(ErSafety.informational("我被霸凌了，我要投诉，投诉流程是什么")).isFalse();
        assertThat(ErSafety.risk("我被霸凌了，我要投诉")).isEqualTo("HUMAN");
    }
    @Test void sickPayNeedsDurationAndPayrollBasis(){assertThat(ask("病假工资怎么计算").answer()).contains("超过6个月","工资基数");}
    @Test void overtimeIsNotAlwaysEqualTimeOff(){assertThat(ask("加班可以一律调休吗").answer()).contains("150%","200%","300%");}
}
