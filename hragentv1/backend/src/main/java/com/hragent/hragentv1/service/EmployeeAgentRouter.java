package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import org.springframework.stereotype.Service;
import java.util.*;

/** Shared deterministic entry for web and authenticated workflow channels. */
@Service
public class EmployeeAgentRouter {
    public record Reply(boolean handled, String answer, String provider, java.util.List<com.hragent.hragentv1.dto.WebChatDtos.ChatAction> actions) {
        public Reply(boolean handled,String answer,String provider){this(handled,answer,provider,java.util.List.of());}
    }
    private final CareConversationGuide care = new CareConversationGuide();
    private final EmployeeRelationsService relations;
    private final EmployeeSelfServiceAssistant services;
    private final HandbookKnowledgeService handbook;
    private final PolicyCopilotService policies;
    private final PersonalAssistantService personal;
    public EmployeeAgentRouter(EmployeeRelationsService relations, EmployeeSelfServiceAssistant services,
            HandbookKnowledgeService handbook, PolicyCopilotService policies, PersonalAssistantService personal) {
        this.relations=relations; this.services=services; this.handbook=handbook; this.policies=policies;
        this.personal=personal;
    }
    public Reply reply(UserAccount user,String text) {
        return reply(user,text,null);
    }
    public Reply reply(UserAccount user,String text,String conversationId) {
        var safety=relations.triage(user,text);
        if (safety.isPresent()) return new Reply(true,safety.get(),"er-human-support");
        if(text!=null && text.contains("年金")) {
            var benefit=policies.benefitAnswer(user,text);
            if(benefit.isPresent())return new Reply(true,benefit.get().answer(),"company-benefit-guidance");
        }
        var guidedCare=care.reply(user,text,conversationId);
        if(guidedCare.isPresent())return new Reply(true,guidedCare.get().answer(),"workplace-support",guidedCare.get().actions());
        var support=relations.supportReply(user,text);
        if (support.isPresent()) return new Reply(true,support.get(),"workplace-support");
        var business=personal.reply(user,text);
        if (business.isPresent()) return new Reply(true,business.get(),"personal-business");
        var catalog=PolicyCatalog.reply(text);
        if (catalog.isPresent()) return new Reply(true,catalog.get(),"policy-catalog");
        var quick=services.quickReply(user,text);
        if (quick.isPresent()) return new Reply(true,quick.get(),"employee-quick-service");
        var document=policies.suppliedAnswer(user,text);
        if(document.isPresent()) return new Reply(true,document.get().answer(),"company-policy-documents");
        var guide=relations.guideReply(user,text);
        if (guide.isPresent()) {
            String answer=guide.get();
            if (!ErSafety.sensitive(text)) {
                var applicable=policies.answer(user,text);
                if ("MATCHED".equals(applicable.status())) answer+="\n\n当前适用的企业制度：\n"+applicable.answer();
                relations.observe(user,text,false);
            }
            return new Reply(true,answer,"er-verified-guidance");
        }
        var local=services.reply(user,text);
        if (local.isPresent()) {
            if (local.get().contains("暂时没有可确认适用于你的制度依据")) {
                var reference=handbook.answer(user,text);
                if (reference.isPresent()) { relations.observe(user,text,true); return referenceReply(user,text,reference.get()); }
            }
            relations.observe(user,text,local.get().contains("暂时没有可确认适用于你的制度依据") || local.get().contains("需要先核实制度版本"));
            return new Reply(true,local.get(),"employee-services");
        }
        var reference=handbook.answer(user,text);
        if (reference.isPresent()) return referenceReply(user,text,reference.get());
        return new Reply(false,"","");
    }
    private Reply referenceReply(UserAccount user,String question,String answer) {
        String sources=String.join("；",handbook.search(user.getTenantId(),question).stream().map(a -> a.getSource()).toList());
        return new Reply(true,answer + (answer.contains("参考出处：") ? "" : "\n\n参考出处：" + sources)
                + "\n版本说明：2022年版手册内容，不等同于现行政策或企业当前执行制度。", "knowledge-reference");
    }
}
