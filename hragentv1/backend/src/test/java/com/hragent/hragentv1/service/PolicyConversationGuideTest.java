package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.UserAccount;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PolicyConversationGuideTest {
    UserAccount user(long tenant,long employee) {var u=mock(UserAccount.class);when(u.getTenantId()).thenReturn(tenant);when(u.getId()).thenReturn(employee);return u;}
    @Test void broadQuestionsAskOneQuestionAndShortRepliesKeepTopic() {
        var guide=new PolicyConversationGuide();var u=user(1,1);
        var answer=guide.opening(u,"我想了解年假","a").orElseThrow();
        assertThat(answer.answer()).doesNotContain("5天","10天","15天");assertThat(answer.actions()).hasSize(3);
        assertThat(guide.resolve(u,"有效期","a")).isEqualTo("年假可以用到什么时候");
    }
    @Test void contextCannotCrossTenantEmployeeOrConversation() {
        var guide=new PolicyConversationGuide();guide.opening(user(1,1),"年假","a");
        assertThat(guide.resolve(user(2,1),"有效期","a")).isEqualTo("有效期");
        assertThat(guide.resolve(user(1,2),"有效期","a")).isEqualTo("有效期");
        assertThat(guide.resolve(user(1,1),"有效期","b")).isEqualTo("有效期");
        guide.resolve(user(1,1),"我最近压力很大","a");
        assertThat(guide.resolve(user(1,1),"有效期","a")).isEqualTo("有效期");
    }
    @Test void cityFollowUpUsesDestinationWithoutChangingEmployeeLocation() {
        var guide=new PolicyConversationGuide();var u=user(1,1);
        assertThat(guide.opening(u,"出差住宿标准是什么","a").orElseThrow().answer()).contains("哪个城市");
        assertThat(guide.resolve(u,"上海","a")).isEqualTo("上海的出差住宿标准是什么");verify(u,never()).setDepartment(any());
    }
    @Test void completeCuratedSummaryAndFullEvidenceArePreserved() {
        var guide=new PolicyConversationGuide();var u=user(1,1);
        String paragraph="累计工龄满1年不满10年：5天；满10年不满20年：10天；满20年：15天。累计病假达到本条门槛时当年不享受。";
        String original="根据文件，相关内容如下。\n\n休假办法｜第六条\n"+paragraph+"\n出处：企业文件 · PDF第3页；文件生效日期：2025-10-14\n\n不能直接认定为个人执行标准。";
        var result=guide.present(u,"年假有几天","a",new EmployeeAgentRouter.Reply(true,original,"company-policy-documents"),"id");
        assertThat(result.answer()).contains("累计工作多久").doesNotContain(paragraph,"根据文件","PDF第3页");assertThat(result.details()).isEqualTo(original);
        var full=guide.present(u,"年假原文","a",new EmployeeAgentRouter.Reply(true,original,"company-policy-documents"),"id");
        assertThat(full.answer()).isEqualTo(original);assertThat(full.details()).isNull();
    }
    @Test void conflictCannotBecomeDefinitiveEntitlement() {
        var guide=new PolicyConversationGuide();String original="这条制度需要先核对版本。\n\n制度｜年假\n年假15天。\n出处：某文件";
        var result=guide.present(user(1,1),"年假","a",new EmployeeAgentRouter.Reply(true,original,"company-policy-documents"),"id");
        assertThat(result.answer()).contains("不能据此确定").doesNotContain("15天");assertThat(result.details()).isEqualTo(original);
    }
    @Test void emotionalSupportAndPersonalResultsAreNotRewritten() {
        var guide=new PolicyConversationGuide();assertThat(guide.opening(user(1,1),"想到年假也很焦虑","a")).isEmpty();
        for(String provider:new String[]{"workplace-support","er-human-support","personal-business"}) {
            var result=guide.present(user(1,1),"年假","a",new EmployeeAgentRouter.Reply(true,"原回复",provider),"id");assertThat(result.answer()).isEqualTo("原回复");assertThat(result.details()).isNull();
        }
    }
    @Test void everyLongPolicyResponseIsBoundedAndSourcesRemainAvailable(){
        var guide=new PolicyConversationGuide();String original="相关文件\n\n规定｜适用条件\n"+"这里包含多种不同情况及其条件。".repeat(50)+"\n出处：企业文件；生效日期：2025-01-01";
        for(String q:new String[]{"报销注意什么","病假需要什么材料","产假有哪些适用条件","加班怎么安排","合同续签有什么条件"}){
            var r=guide.present(user(1,1),q,"b",new EmployeeAgentRouter.Reply(true,original,"company-policy-documents"),"id");assertThat(r.answer().length()).isLessThanOrEqualTo(230);assertThat(r.details()).isEqualTo(original);
        }
    }
    @Test void lodgingFollowupKeepsCityAndShowsOnlySelectedRole(){
        var g=new PolicyConversationGuide();var u=user(1,1);g.opening(u,"住宿标准","a");
        String source="规定｜酒店住宿\n北京、上海、广州、深圳：中层干部每人每晚最高950元，员工900元；其他城市：中层干部750元，员工700元。限额内按实际住宿费用报销，并非定额补贴。附件二指定城市在旺季最多上浮20%；因公超限须说明原因并获批。确认你的标准需要目的地、住宿日期及是否为中心副总监及以上。\n出处：企业文件";
        var city=g.present(u,g.resolve(u,"上海","a"),"a",new EmployeeAgentRouter.Reply(true,source,"company-policy-documents"),"id");assertThat(city.answer()).contains("普通员工还是中层干部");
        String follow=g.resolve(u,"普通员工","a");assertThat(follow).contains("上海","普通员工");
        var matched=g.present(u,follow,"a",new EmployeeAgentRouter.Reply(true,source,"company-policy-documents"),"id");assertThat(matched.answer()).contains("900 元/人晚").doesNotContain("950元","750元","700元","哪个城市");assertThat(matched.details()).isEqualTo(source);
    }
}
