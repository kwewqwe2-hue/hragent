package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeRelationsDtos.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static com.hragent.hragentv1.service.EmployeeServiceSupport.*;
import static com.hragent.hragentv1.service.ErSafety.has;

@Service
public class EmployeeRelationsService {
    private final ErSettingsRepository settings;
    private final ErCaseRepository cases;
    private final ErJourneyTaskRepository tasks;
    private final ErPreferenceRepository preferences;
    private final ErSignalRepository signals;
    private final ErKnowledgeGapRepository gaps;
    private final UserAccountRepository users;
    private final KnowledgeArticleRepository articles;
    private final SecretCryptoService crypto;
    private final AuditService audit;
    private final ErKnowledge knowledge;
    public EmployeeRelationsService(ErSettingsRepository settings, ErCaseRepository cases, ErJourneyTaskRepository tasks,
            ErPreferenceRepository preferences, ErSignalRepository signals, ErKnowledgeGapRepository gaps,
            UserAccountRepository users, KnowledgeArticleRepository articles, SecretCryptoService crypto,
            AuditService audit, ErKnowledge knowledge) {
        this.settings=settings; this.cases=cases; this.tasks=tasks; this.preferences=preferences;
        this.signals=signals; this.gaps=gaps; this.users=users; this.articles=articles;
        this.crypto=crypto; this.audit=audit; this.knowledge=knowledge;
    }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    private static LocalDate week() { return EmployeeReminderService.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }
    private ErSettings config(Long tenantId) { return settings.findByTenantId(tenantId).orElseGet(ErSettings::new); }
    private void requireHr(UserAccount user) { if (user.getRole()!=Role.HR) throw AppException.forbidden("仅 HR 可访问组织运营后台"); }
    private List<Long> investigators(Long tenantId) {
        String ids=config(tenantId).getInvestigatorIds();
        if (blank(ids)) return List.of();
        return Arrays.stream(ids.split(",")).map(Long::valueOf).filter(id -> users.findById(id)
                .filter(u -> tenantId.equals(u.getTenantId()) && u.isActive() && u.getRole()==Role.HR).isPresent()).toList();
    }
    private void requireInvestigator(UserAccount user) {
        if (user.getRole()!=Role.HR || !investigators(user.getTenantId()).contains(user.getId()))
            throw AppException.forbidden("仅本企业指定的 ER 调查人员可查看和处理工单");
    }
    public Resources resources(UserAccount user) {
        var c=config(user.getTenantId()); var ids=investigators(user.getTenantId());
        boolean enabled=preferences.findByTenantIdAndEmployeeId(user.getTenantId(),user.getId()).map(ErPreference::getAnalyticsEnabled).orElse(false);
        return new Resources(c.getEapName(),c.getEapPhone(),c.getEapUrl(),c.getErContact(),!ids.isEmpty(),
                user.getRole()==Role.HR && ids.contains(user.getId()),enabled);
    }
    public SettingsInput settings(UserAccount user) {
        requireHr(user); var c=config(user.getTenantId());
        return new SettingsInput(c.getEapName(),c.getEapPhone(),c.getEapUrl(),c.getErContact(),investigators(user.getTenantId()));
    }
    @Transactional
    public SettingsInput saveSettings(UserAccount user, SettingsInput input) {
        requireHr(user); requireHttpUrl(input.eapUrl());
        var ids=input.investigatorIds().stream().distinct().toList();
        for (Long id:ids) if (users.findById(id).filter(u -> user.getTenantId().equals(u.getTenantId()) && u.isActive() && u.getRole()==Role.HR).isEmpty())
            throw AppException.badRequest("调查人员必须为本企业在职 HR");
        var c=config(user.getTenantId()); c.setTenantId(user.getTenantId());
        c.setEapName(input.eapName()); c.setEapPhone(input.eapPhone()); c.setEapUrl(input.eapUrl()); c.setErContact(input.erContact());
        c.setInvestigatorIds(String.join(",",ids.stream().map(String::valueOf).toList())); settings.save(c);
        audit.log(user,"ER_SETTINGS_UPDATED","er_settings",c.getId(),"更新 EAP、ER 联系方式与授权调查人员");
        return settings(user);
    }
    public List<ErKnowledge.Guide> guides() { return knowledge.all(); }
    private static final List<String[]> JOURNEY=List.of(
        new String[]{"day1-account","第一天","确认账号与设备","核对办公地点、报到联系人和设备清单；账号异常联系 IT。","0"},
        new String[]{"day1-buddy","第一天","认识导师与团队","找到可以请教的同事，约定一次简短的欢迎交流。","0"},
        new String[]{"week-policy","第一周","了解考勤与请假","阅读适用制度，确认打卡、请假和报销入口。","6"},
        new String[]{"week-tools","第一周","申请协作工具权限","列出系统、用途和权限范围，通过正式渠道申请。","6"},
        new String[]{"week-culture","第一周","熟悉团队协作约定","确认文档存放位置、例会与沟通习惯，不懂的术语可以直接问。","6"},
        new String[]{"month-goals","第一个月","和导师确认目标","把本月工作与学习目标写下来，明确支持资源。","29"},
        new String[]{"month-feedback","第一个月","安排一次双向反馈","谈谈顺利的事和遇到的困难；转正日期以 HR 档案为准。","29"},
        new String[]{"exit-work","离职准备","整理工作与项目交接","记录项目进度、资料位置和交接人，不移动无权限的数据。","-1"},
        new String[]{"exit-equipment","离职准备","核对设备与账号","与 IT 确认归还清单和停用日期，由正式流程办理。","-1"},
        new String[]{"exit-settlement","离职准备","核对结算与证明","向 HR 确认工资、未结报销、社保减员月份和证明。","-1"},
        new String[]{"exit-agreement","离职准备","查阅保密与竞业约定","涉及义务或争议时申请 ER 咨询，不自行推定协议有效性。","-1"});
    private List<String[]> journeyDefinitions(UserAccount user) {
        LocalDate today=EmployeeReminderService.today();
        if(WorkJourney.onboarding(user.getEntryDate(),today))return JOURNEY;
        var definitions=new ArrayList<>(WorkJourney.weekly(today));
        definitions.addAll(JOURNEY.stream().filter(t->t[1].equals("离职准备")).toList());
        return definitions;
    }
    public List<JourneyTask> journey(UserAccount user) {
        var saved=tasks.findByTenantIdAndEmployeeId(user.getTenantId(),user.getId());
        return journeyDefinitions(user).stream().map(t -> new JourneyTask(t[0],t[1],t[2],t[3],
                saved.stream().anyMatch(s -> s.getTaskKey().equals(t[0]) && s.getDone()),
                user.getEntryDate()==null || Integer.parseInt(t[4])<0 ? null : user.getEntryDate().plusDays(Integer.parseInt(t[4])))).toList();
    }
    @Transactional
    public List<JourneyTask> setTask(UserAccount user, String key, boolean done) {
        if (journeyDefinitions(user).stream().noneMatch(t -> t[0].equals(key))) throw AppException.badRequest("任务已更新，请刷新后操作");
        var t=tasks.findByTenantIdAndEmployeeIdAndTaskKey(user.getTenantId(),user.getId(),key).orElseGet(ErJourneyTask::new);
        t.setTenantId(user.getTenantId()); t.setEmployeeId(user.getId()); t.setTaskKey(key); t.setDone(done); t.setUpdatedAt(now()); tasks.save(t);
        return journey(user);
    }
    private ErCase newCase(UserAccount user, String kind, boolean anonymous, String body, String receipt) {
        var c=new ErCase(); c.setTenantId(user.getTenantId()); c.setAnonymous(anonymous);
        c.setEmployeeId(anonymous ? null : user.getId()); c.setKind(kind); c.setStatus("OPEN");
        c.setEncryptedBody(crypto.encrypt(body)); c.setReceiptHash(receipt==null ? null : hash(receipt));
        c.setCreatedAt(now()); c.setUpdatedAt(now()); return cases.save(c);
    }
    @Transactional
    public Receipt submit(UserAccount user, CaseInput input) {
        if (!input.consent()) throw AppException.badRequest("请先阅读隐私说明并同意提交");
        if (investigators(user.getTenantId()).isEmpty()) throw AppException.badRequest("企业尚未指定调查人员，暂不收集敏感申诉。请使用已公布的 ER 联系方式或外部求助渠道。");
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=input.anonymous() ? Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) : null;
        String body="事实描述：\n"+input.facts()+"\n\n时间："+display(input.occurredAt())+"\n地点："+display(input.location())
                +"\n证据线索（待核实）：\n"+display(input.evidence())+"\n希望获得的帮助：\n"+display(input.expectation());
        var c=newCase(user,input.kind(),input.anonymous(),body,token);
        // Do not write anonymous submitter identity into the general audit trail.
        return new Receipt(view(c,true),token,input.anonymous()
                ? "已进入指定调查组队列。请立即保存查询码；它仅显示一次，遗失后无法通过账号找回。"
                : "已进入指定调查组队列，等待人工接单。你可以在此查看回复；尚不代表已有人员在线。 ");
    }
    public List<CaseView> mine(UserAccount user) {
        return cases.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(user.getTenantId(),user.getId()).stream().map(c -> view(c,true)).toList();
    }
    public CaseView lookup(UserAccount user, String receipt) {
        return view(cases.findByTenantIdAndReceiptHash(user.getTenantId(),hash(receipt))
                .orElseThrow(() -> AppException.notFound("查询码无效或工单不存在")),true);
    }
    public List<CaseView> queue(UserAccount user) {
        requireInvestigator(user);
        return cases.findByTenantIdOrderByCreatedAtDesc(user.getTenantId()).stream().map(c -> view(c,false)).toList();
    }
    public CaseView detail(UserAccount user, Long id) {
        requireInvestigator(user); var c=caseInTenant(user,id);
        audit.log(user,"ER_CASE_READ","er_case",id,"授权调查人员查看加密内容"); return view(c,true);
    }
    private ErCase caseInTenant(UserAccount user, Long id) {
        return cases.findByIdAndTenantId(id,user.getTenantId()).orElseThrow(() -> AppException.notFound("工单不存在"));
    }
    @Transactional
    public CaseView process(UserAccount user, Long id, CaseAction input) {
        requireInvestigator(user); var c=caseInTenant(user,id);
        if ("RESOLVED".equals(c.getStatus())) throw AppException.badRequest("工单已结案；员工补充后可重新处理");
        if (c.getAssignedTo()!=null && !c.getAssignedTo().equals(user.getId()) && investigators(user.getTenantId()).contains(c.getAssignedTo()))
            throw AppException.forbidden("该工单已由其他调查人员接单");
        c.setAssignedTo(user.getId()); c.setStatus(input.status());
        c.setEncryptedReply(crypto.encrypt(crypto.decrypt(c.getEncryptedReply())+"\n\n"+now()+" · ER 人工回复\n"+input.reply()));
        c.setUpdatedAt(now()); cases.save(c);
        audit.log(user,"ER_CASE_UPDATED","er_case",id,"更新处理状态："+input.status()); return view(c,true);
    }
    @Transactional
    public CaseView followup(UserAccount user, Long id, Followup input) {
        var c=caseInTenant(user,id);
        if (c.getAnonymous()) {
            if (blank(input.receipt()) || !MessageDigest.isEqual(hash(input.receipt()).getBytes(StandardCharsets.UTF_8),c.getReceiptHash().getBytes(StandardCharsets.UTF_8)))
                throw AppException.notFound("查询码无效或工单不存在");
        } else if (!user.getId().equals(c.getEmployeeId())) throw AppException.notFound("工单不存在");
        append(c,input.message()); if ("RESOLVED".equals(c.getStatus())) c.setStatus("OPEN"); cases.save(c); return view(c,true);
    }
    private void append(ErCase c, String text) {
        String body=crypto.decrypt(c.getEncryptedBody());
        if (body.length()+text.length()>30000) throw AppException.badRequest("该工单补充内容已达上限，请等待人工回复或另建工单");
        c.setEncryptedBody(crypto.encrypt(body+"\n\n"+now()+" · 补充\n"+text)); c.setUpdatedAt(now());
    }
    private CaseView view(ErCase c, boolean detail) {
        String employee=c.getAnonymous() ? "匿名员工" : detail ? users.findById(c.getEmployeeId())
                .filter(u -> c.getTenantId().equals(u.getTenantId())).map(u -> u.getName()+"（"+u.getEmployeeNo()+"）").orElse("员工") : "实名员工";
        return new CaseView(c.getId(),c.getKind(),c.getStatus(),c.getAnonymous(),employee,
                detail ? crypto.decrypt(c.getEncryptedBody()) : "",detail ? crypto.decrypt(c.getEncryptedReply()) : "",
                c.getAssignedTo(),c.getCreatedAt(),c.getUpdatedAt());
    }
    static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public Optional<String> triage(UserAccount user, String message) {
        String risk=ErSafety.risk(message);
        boolean urgent="URGENT".equals(risk);
        var active=cases.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(user.getTenantId(),user.getId()).stream()
                .filter(c -> has(c.getKind(),"URGENT","HANDOFF") && !"RESOLVED".equals(c.getStatus())).findFirst();
        boolean trigger=!"NONE".equals(risk) && !ErSafety.informational(message);
        if (!trigger && active.isEmpty()) return Optional.empty();
        String support=urgent ? "我很在意你现在是否安全。如果你此刻可能伤害自己，请先远离危险的位置或物品，马上联系身边可信任的人陪着你。"
                +"中国大陆有紧急危险请拨打 120 或 110；也可以拨打 12356 寻求心理援助。你现在身边有人可以陪你吗？\n\n"
                : "谢谢你愿意说出来。这件事值得被认真对待，我会先暂停自动判断和办理，由专业人员进一步了解事实。\n\n";
        if (active.isPresent()) {
            var c=active.get(); append(c,message); if (urgent) c.setKind("URGENT"); cases.save(c);
            return Optional.of(support+"你的补充已加密加入人工工单 #"+c.getId()+"。"
                    +(investigators(user.getTenantId()).isEmpty() ? "目前暂无可用调查人员，请使用 ER 联系方式。" : "当前状态："+("IN_PROGRESS".equals(c.getStatus()) ? "人工处理中" : "等待人工接单")+"。")
                    +"可在「合规与申诉 → 我的工单」查看人工回复。紧急情况请直接求助，不要等待工单。 "+contact(user));
        }
        if (investigators(user.getTenantId()).isEmpty()) return Optional.of(support
                +"企业尚未配置 ER 接管人员，因此本次没有创建或转交工单，也没有人正在实时接管。"+contact(user)
                +"\n可在「合规与申诉」查看其他求助方式。"+(urgent ? "\n[心理援助热线来源] ("+ErKnowledge.HOTLINE_URL+")" : ""));
        var c=newCase(user,urgent ? "URGENT" : "HANDOFF",false,"员工消息（待人工核实）：\n"+message,null);
        return Optional.of(support+"已创建实名人工支持工单 #"+c.getId()+"，消息加密保存，仅指定 ER 调查人员可查看，当前等待人工接单。"
                +"后续消息将加入该工单，结案前暂停自动业务回答。可在「合规与申诉」查看状态和回复。"+contact(user));
    }
    private String contact(UserAccount user) {
        String value=config(user.getTenantId()).getErContact(); return blank(value) ? "" : "\n企业 ER 联系方式："+value;
    }
    public Optional<String> guideReply(UserAccount user, String message) {
        return knowledge.match(ErSafety.normalize(message)).map(g -> {
            String reply=knowledge.render(g);
            if (g.key().equals("care")) {
                var r=resources(user); reply+=blank(r.eapPhone()) && blank(r.eapUrl()) ? "\n\n企业 EAP 暂未配置，不能确认可用预约渠道。"
                        : "\n\n企业 EAP："+display(r.eapName())+(blank(r.eapPhone()) ? "" : "\n电话："+r.eapPhone())+(blank(r.eapUrl()) ? "" : "\n[预约咨询]("+r.eapUrl()+")");
            }
            return reply;
        });
    }
    public Optional<String> supportReply(UserAccount user, String message) {
        return WorkplaceSupport.reply(message).map(answer -> {
            if (!WorkplaceSupport.emotional(message)) return answer;
            var r=resources(user);
            if (!blank(r.eapPhone()) || !blank(r.eapUrl())) answer += "\n\n如果你愿意，也可以使用企业提供的支持："+display(r.eapName())
                    +(blank(r.eapPhone()) ? "" : "，电话："+r.eapPhone())+(blank(r.eapUrl()) ? "" : "\n预约渠道："+r.eapUrl());
            return answer;
        });
    }
    @Transactional
    public Resources consent(UserAccount user, boolean enabled) {
        var p=preferences.findByTenantIdAndEmployeeId(user.getTenantId(),user.getId()).orElseGet(ErPreference::new);
        p.setTenantId(user.getTenantId()); p.setEmployeeId(user.getId()); p.setAnalyticsEnabled(enabled); preferences.save(p);
        if (!enabled) for (int i=0;i<9;i++) signals.deleteByTenantIdAndParticipant(user.getTenantId(),participant(user,week().minusWeeks(i)));
        return resources(user);
    }
    private String participant(UserAccount user, LocalDate week) {
        return crypto.fingerprint("er-aggregate:"+user.getTenantId()+":"+user.getId()+":"+week);
    }
    @Transactional
    public void observe(UserAccount user, String question, boolean unresolved) {
        if (ErSafety.sensitive(question)) return;
        String topic=ErSafety.topic(question);
        if (unresolved) {
            var g=gaps.findByTenantIdAndTopic(user.getTenantId(),topic).orElseGet(ErKnowledgeGap::new);
            g.setTenantId(user.getTenantId()); g.setTopic(topic); g.setStatus("OPEN"); g.setOccurrences(g.getOccurrences()+1); g.setUpdatedAt(now()); gaps.save(g);
        }
        if (!resources(user).analyticsEnabled()) return;
        LocalDate week=week(); String participant=participant(user,week);
        var s=signals.findByTenantIdAndParticipantAndTopicAndWeek(user.getTenantId(),participant,topic,week).orElseGet(ErSignal::new);
        s.setTenantId(user.getTenantId()); s.setDepartment(blank(user.getDepartment()) ? "未分配部门" : user.getDepartment());
        s.setTopic(topic); s.setParticipant(participant); s.setWeek(week); s.setUnresolved(s.getUnresolved() || unresolved); signals.save(s);
        signals.deleteByWeekBefore(week.minusWeeks(7));
    }
    public List<ErKnowledgeGap> gaps(UserAccount user) { requireHr(user); return gaps.findByTenantIdOrderByUpdatedAtDesc(user.getTenantId()); }
    @Transactional
    public void gapStatus(UserAccount user, Long id, String status) {
        requireHr(user); var g=gaps.findByIdAndTenantId(id,user.getTenantId()).orElseThrow(() -> AppException.notFound("知识待办不存在"));
        g.setStatus(status); g.setUpdatedAt(now()); gaps.save(g);
        audit.log(user,"ER_KNOWLEDGE_GAP_UPDATED","er_knowledge_gap",id,"知识维护状态："+status);
    }
    public Insights insights(UserAccount user) {
        requireHr(user); LocalDate current=week(); var all=signals.findByTenantIdAndWeekGreaterThanEqual(user.getTenantId(),current.minusWeeks(1));
        List<TopicMetric> rows=new ArrayList<>();
        var keys=all.stream().filter(s -> current.equals(s.getWeek())).map(s -> List.of(s.getDepartment(),s.getTopic())).distinct().toList();
        for (var key:keys) {
            long count=all.stream().filter(s -> current.equals(s.getWeek()) && key.get(0).equals(s.getDepartment()) && key.get(1).equals(s.getTopic())).map(ErSignal::getParticipant).distinct().count();
            if (count<5) continue;
            long prev=all.stream().filter(s -> current.minusWeeks(1).equals(s.getWeek()) && key.get(0).equals(s.getDepartment()) && key.get(1).equals(s.getTopic())).map(ErSignal::getParticipant).distinct().count();
            rows.add(new TopicMetric(key.get(0),key.get(1),count,prev<5 ? -1 : prev,
                    prev<5 ? "上期样本不足，暂不比较" : count>=prev*1.5 ? "咨询需求上升，建议了解团队支持需求" : "暂无明显上升"));
        }
        return new Insights(rows,5,current,"仅统计自愿参与者的每周去重咨询主题；每个部门主题至少 5 人才显示。排除心理支持与申诉正文，不提供个人离职预测或情绪诊断。历史信号最多保留 8 周。");
    }
    @Transactional
    public int seedDrafts(UserAccount user) {
        requireHr(user); var existing=articles.findByTenantIdOrderByUpdatedAtDesc(user.getTenantId()); int added=0;
        for (var guide:knowledge.all()) {
            String title="[待核实] "+guide.title();
            if (existing.stream().anyMatch(a -> title.equals(a.getTitle()))) continue;
            var a=new KnowledgeArticle(); a.setTenantId(user.getTenantId()); a.setTitle(title); a.setCategory("员工关系服务参考");
            a.setContent(guide.content()+"\n\nHR 待补充：企业负责人、办理渠道、适用地区/主体、制度原文、生效日期及复核时间。");
            a.setSource(guide.source()); a.setSourceUrl(guide.url()); a.setReviewStatus("DRAFT"); a.setUpdatedAt(EmployeeReminderService.today()); articles.save(a); added++;
        }
        audit.log(user,"ER_KNOWLEDGE_DRAFTS","knowledge",null,"导入服务参考草稿 "+added+" 条，待 HR 审核"); return added;
    }
}
