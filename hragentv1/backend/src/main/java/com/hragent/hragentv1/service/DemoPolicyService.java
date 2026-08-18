package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.DemoPolicySourceState;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.DemoPolicyDtos;
import com.hragent.hragentv1.repo.DemoPolicySourceStateRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class DemoPolicyService {
    private static final long STATE_ID = 1L;
    private static final int LATEST_VERSION = 2;
    private static final String DISCLAIMER = "本页面仅用于 HR Agent 功能演示，不代表任何真实政府机关或现行政策。";

    private final DemoPolicySourceStateRepository stateRepository;
    private final AuditService auditService;

    public DemoPolicyService(
            DemoPolicySourceStateRepository stateRepository,
            AuditService auditService
    ) {
        this.stateRepository = stateRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DemoPolicyDtos.PolicyView current() {
        return view(requireState());
    }

    @Transactional
    public DemoPolicyDtos.PolicyView publishNext(UserAccount actor) {
        requireHr(actor);
        DemoPolicySourceState state = requireState();
        if (state.getCurrentVersion() < LATEST_VERSION) {
            state.setCurrentVersion(LATEST_VERSION);
            state.setUpdatedAt(LocalDateTime.now());
            stateRepository.save(state);
            auditService.log(
                    actor,
                    "PUBLISH_DEMO_POLICY",
                    "demo_policy_source",
                    STATE_ID,
                    "发布政策演示站版本 2026.2"
            );
        }
        return view(state);
    }

    @Transactional
    public DemoPolicyDtos.PolicyView reset(UserAccount actor) {
        requireHr(actor);
        DemoPolicySourceState state = requireState();
        if (state.getCurrentVersion() != 1) {
            state.setCurrentVersion(1);
            state.setUpdatedAt(LocalDateTime.now());
            stateRepository.save(state);
            auditService.log(
                    actor,
                    "RESET_DEMO_POLICY",
                    "demo_policy_source",
                    STATE_ID,
                    "重置政策演示站为版本 2026.1"
            );
        }
        return view(state);
    }

    private DemoPolicySourceState requireState() {
        return stateRepository.findById(STATE_ID).orElseGet(() -> {
            DemoPolicySourceState state = new DemoPolicySourceState();
            state.setId(STATE_ID);
            state.setCurrentVersion(1);
            state.setUpdatedAt(LocalDateTime.now());
            return stateRepository.save(state);
        });
    }

    private DemoPolicyDtos.PolicyView view(DemoPolicySourceState state) {
        PolicyDefinition policy = definition(state.getCurrentVersion());
        return new DemoPolicyDtos.PolicyView(
                "demo-employment-policy",
                "示例市人力资源政策演示中心",
                "DEMO",
                policy.title(),
                policy.version(),
                "示例市",
                policy.publishedAt(),
                policy.effectiveAt(),
                policy.summary(),
                policy.content(),
                policy.changeSummary(),
                sha256(policy.title() + "\n" + policy.version() + "\n" + policy.content()),
                state.getUpdatedAt(),
                state.getCurrentVersion() < LATEST_VERSION,
                DISCLAIMER
        );
    }

    private PolicyDefinition definition(int version) {
        if (version >= LATEST_VERSION) {
            return new PolicyDefinition(
                    "关于完善企业病假材料管理的演示通知",
                    "2026.2",
                    LocalDate.of(2026, 8, 11),
                    LocalDate.of(2026, 9, 1),
                    "演示版本新增连续病假材料补充与电子材料留存规则。",
                    "一、员工申请病假时，应提交可核验的医疗材料。\n"
                            + "二、连续病假达到三个工作日时，企业可要求补充病历或诊断建议。\n"
                            + "三、电子病假材料可以作为预审依据，原件要求由企业制度另行规定。\n"
                            + "四、企业处理健康信息时，应遵循最小必要原则并限制访问范围。",
                    "新增第二、三条，并补充健康信息最小必要原则。"
            );
        }
        return new PolicyDefinition(
                "关于规范企业病假材料管理的演示通知",
                "2026.1",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 15),
                "演示企业应建立可核验、可追溯的病假材料管理流程。",
                "一、员工申请病假时，应提交可核验的医疗材料。\n"
                        + "二、企业应妥善保存审批记录，并仅向授权人员开放。",
                "初始演示版本。"
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireHr(UserAccount actor) {
        if (actor == null || actor.getRole() != Role.HR) {
            throw AppException.forbidden("只有空间管理员可以操作政策演示数据源");
        }
    }

    private record PolicyDefinition(
            String title,
            String version,
            LocalDate publishedAt,
            LocalDate effectiveAt,
            String summary,
            String content,
            String changeSummary
    ) {
    }
}
