package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EmployeeChecklistService {
    private final PolicyCopilotService policies;
    private final EmployeeServiceProfileService profiles;
    public EmployeeChecklistService(PolicyCopilotService policies, EmployeeServiceProfileService profiles) {
        this.policies = policies; this.profiles = profiles;
    }
    public Checklist generate(UserAccount user, ChecklistRequest input) {
        String topic = switch (input.kind()) {
            case "SOCIAL_SECURITY" -> "社保转移接续";
            case "HOUSING_FUND" -> "公积金异地转移";
            case "SETTLEMENT" -> "落户";
            default -> throw com.hragent.hragentv1.web.AppException.badRequest("不支持的办理事项");
        };
        var profile = profiles.profile(user);
        var destinationProfile = new ProfileView(profile.employeeId(), profile.employeeNo(), profile.name(),
                input.destination().trim(), profile.jobGrade(), profile.workType(), profile.legalEntity(),
                profile.probationEndDate(), profile.contractEndDate(), profile.medicalCheckDeadline(),
                profile.annualLeaveExpiresAt(), profile.medicalBookingUrl(), profile.missingAttributes());
        var policy = policies.answer(user, topic, destinationProfile);
        var materials = new ArrayList<String>();
        materials.add("本人身份信息（证件种类、原件或复印件要求需向受理机构核实）");
        if ("SETTLEMENT".equals(input.kind())) {
            materials.add("拟申请的落户路径及个人条件说明，供受理机构核对资格");
            materials.add("工作、参保、居住及学历等已有记录，具体提交范围以对应路径清单为准");
        } else {
            materials.add("转出地、转入地及两地相关账户信息，供核对是否具备转移条件");
            materials.add("当前参保或缴存状态、劳动关系变更信息，具体表单由受理机构确认");
        }
        var steps = List.of("先确认办理城市、事项和受理机构，核实适用条件。",
                "核对下方制度出处、生效日期及材料条款；没有匹配依据时请 HR 补充官方办事指南。",
                "按照受理机构要求准备材料，在其官方渠道提交并保存回执；本页只生成准备清单。",
                "凭回执查询办理进度；补件要求以受理机构通知为准。");
        String title = input.destination().trim() + " · " + topic + "准备清单";
        String sourceText = policy.citations().isEmpty() ? "尚无经 HR 核实且生效的目的地指南。此清单不用于判断资格，也不是官方完整材料清单。"
                : String.join("\n\n", policy.citations().stream().map(c -> "《" + c.title() + "》\n出处：" + c.source()
                        + "；生效日期：" + c.effectiveFrom() + "\n" + Objects.toString(c.sourceUrl(), "") + "\n" + c.excerpt()).toList());
        String markdown = "# " + title + "\n\n生成日期：" + EmployeeReminderService.today()
                + "\n转出地：" + EmployeeServiceSupport.display(input.origin()) + "\n目的地：" + input.destination()
                + "\n\n## 准备材料（待受理机构确认）\n\n- [ ] " + String.join("\n- [ ] ", materials)
                + "\n\n## 办理步骤\n\n" + String.join("\n\n", steps)
                + "\n\n## 已匹配依据\n\n" + sourceText;
        return new Checklist(title, policy.citations().isEmpty() ? "NEEDS_HR" : "PREPARATION_ONLY",
                input.origin(), input.destination(), materials, steps, policy.citations(), markdown);
    }
}
