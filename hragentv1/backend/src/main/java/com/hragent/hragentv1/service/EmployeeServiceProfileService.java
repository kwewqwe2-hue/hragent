package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.EmployeeServiceDtos.*;
import com.hragent.hragentv1.repo.*;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import static com.hragent.hragentv1.service.EmployeeServiceSupport.blank;

@Service
public class EmployeeServiceProfileService {
    private final EmployeeServiceProfileRepository profiles;
    private final EmployeePersonalProfileRepository personalProfiles;
    private final UserAccountRepository users;
    private final AuditService audit;
    public EmployeeServiceProfileService(EmployeeServiceProfileRepository profiles,
            EmployeePersonalProfileRepository personalProfiles, UserAccountRepository users, AuditService audit) {
        this.profiles = profiles; this.personalProfiles = personalProfiles; this.users = users; this.audit = audit;
    }
    public ProfileView profile(UserAccount employee) {
        var profile = profiles.findByTenantIdAndEmployeeId(employee.getTenantId(), employee.getId())
                .orElseGet(EmployeeServiceProfile::new);
        var personal = personalProfiles.findByTenantIdAndEmployeeId(employee.getTenantId(), employee.getId())
                .orElseGet(EmployeePersonalProfile::new);
        String location = blank(profile.getLocation()) ? personal.getWorkLocation() : profile.getLocation();
        var missing = new ArrayList<String>();
        if (blank(location)) missing.add("工作地点");
        if (blank(profile.getJobGrade())) missing.add("职级");
        if (blank(profile.getWorkType())) missing.add("工种");
        if (blank(profile.getLegalEntity())) missing.add("合同主体");
        return new ProfileView(employee.getId(), employee.getEmployeeNo(), employee.getName(), location,
                profile.getJobGrade(), profile.getWorkType(), profile.getLegalEntity(), profile.getProbationEndDate(),
                personal.getContractEndDate(), profile.getMedicalCheckDeadline(), profile.getAnnualLeaveExpiresAt(),
                profile.getMedicalBookingUrl(), missing);
    }
    public UserAccount requireEmployee(UserAccount actor, Long employeeId) {
        if (actor.getRole() != Role.HR) throw AppException.forbidden("只有 HR 可以维护服务档案");
        return users.findById(employeeId).filter(u -> u.getTenantId().equals(actor.getTenantId()))
                .orElseThrow(() -> AppException.notFound("员工不存在"));
    }
    @Transactional
    public ProfileView update(UserAccount actor, Long employeeId, ProfileRequest input) {
        UserAccount employee = requireEmployee(actor, employeeId);
        EmployeeServiceSupport.requireHttpUrl(input.medicalBookingUrl());
        var profile = profiles.findByTenantIdAndEmployeeId(actor.getTenantId(), employeeId)
                .orElseGet(EmployeeServiceProfile::new);
        profile.setTenantId(actor.getTenantId()); profile.setEmployeeId(employeeId);
        profile.setLocation(clean(input.location())); profile.setJobGrade(clean(input.jobGrade()));
        profile.setWorkType(clean(input.workType())); profile.setLegalEntity(clean(input.legalEntity()));
        profile.setProbationEndDate(input.probationEndDate());
        profile.setMedicalCheckDeadline(input.medicalCheckDeadline());
        profile.setAnnualLeaveExpiresAt(input.annualLeaveExpiresAt());
        profile.setMedicalBookingUrl(clean(input.medicalBookingUrl()));
        profiles.save(profile);
        audit.log(actor, "UPDATE_EMPLOYEE_SERVICE_PROFILE", "employee", employeeId, "维护制度适用属性与事件日期");
        return profile(employee);
    }
    private String clean(String value) { return blank(value) ? null : value.trim(); }
}
