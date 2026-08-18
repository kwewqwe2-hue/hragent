package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.EmployeePersonalProfile;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.EmployeePersonalProfileDtos;
import com.hragent.hragentv1.repo.EmployeePersonalProfileRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.web.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EmployeePersonalProfileService {
    private final EmployeePersonalProfileRepository profileRepository;
    private final UserAccountRepository userAccountRepository;
    private final SecretCryptoService secretCryptoService;
    private final AuditService auditService;

    public EmployeePersonalProfileService(
            EmployeePersonalProfileRepository profileRepository,
            UserAccountRepository userAccountRepository,
            SecretCryptoService secretCryptoService,
            AuditService auditService
    ) {
        this.profileRepository = profileRepository;
        this.userAccountRepository = userAccountRepository;
        this.secretCryptoService = secretCryptoService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public EmployeePersonalProfileDtos.ProfileView mine(UserAccount actor) {
        EmployeePersonalProfile profile = profileRepository
                .findByTenantIdAndEmployeeId(actor.getTenantId(), actor.getId())
                .orElse(null);
        return view(actor, profile);
    }

    @Transactional(readOnly = true)
    public List<EmployeePersonalProfileDtos.ProfileSummary> list(UserAccount actor) {
        requireHr(actor);
        Map<Long, EmployeePersonalProfile> profiles = new HashMap<>();
        profileRepository.findByTenantIdOrderByEmployeeIdAsc(actor.getTenantId())
                .forEach(profile -> profiles.put(profile.getEmployeeId(), profile));
        return userAccountRepository.findByTenantIdOrderByIdAsc(actor.getTenantId()).stream()
                .map(employee -> summary(employee, profiles.get(employee.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeePersonalProfileDtos.ProfileView detail(UserAccount actor, Long employeeId) {
        requireHr(actor);
        UserAccount employee = requireTenantEmployee(actor.getTenantId(), employeeId);
        EmployeePersonalProfile profile = profileRepository
                .findByTenantIdAndEmployeeId(actor.getTenantId(), employeeId)
                .orElse(null);
        return view(employee, profile);
    }

    @Transactional
    public EmployeePersonalProfileDtos.ProfileView update(
            UserAccount actor,
            Long employeeId,
            EmployeePersonalProfileDtos.ProfileUpdateRequest request
    ) {
        requireHr(actor);
        validate(request);
        UserAccount employee = requireTenantEmployee(actor.getTenantId(), employeeId);
        EmployeePersonalProfile profile = profileRepository
                .findByTenantIdAndEmployeeId(actor.getTenantId(), employeeId)
                .orElseGet(() -> {
                    EmployeePersonalProfile created = new EmployeePersonalProfile();
                    created.setTenantId(actor.getTenantId());
                    created.setEmployeeId(employeeId);
                    return created;
                });

        profile.setLegalName(clean(request.legalName()));
        profile.setEnglishName(clean(request.englishName()));
        profile.setGender(clean(request.gender()));
        profile.setBirthDate(request.birthDate());
        profile.setNationality(clean(request.nationality()));
        profile.setIdType(clean(request.idType()));
        profile.setIdNumberEncrypted(secretCryptoService.encrypt(clean(request.idNumber())));
        profile.setPassportNumberEncrypted(secretCryptoService.encrypt(clean(request.passportNumber())));
        profile.setPassportExpiryDate(request.passportExpiryDate());
        profile.setEmploymentType(clean(request.employmentType()));
        profile.setContractStartDate(request.contractStartDate());
        profile.setContractEndDate(request.contractEndDate());
        profile.setWorkLocation(clean(request.workLocation()));
        profile.setMonthlySalary(normalizeSalary(request.monthlySalary()));
        profile.setCurrency(normalizeCurrency(request.currency(), request.monthlySalary()));
        profile.setHomeAddress(clean(request.homeAddress()));
        profile.setEmergencyContactName(clean(request.emergencyContactName()));
        profile.setEmergencyContactPhone(clean(request.emergencyContactPhone()));
        profile.setUpdatedByEmployeeId(actor.getId());
        profile.setUpdatedAt(LocalDateTime.now());

        EmployeePersonalProfile saved = profileRepository.save(profile);
        auditService.log(
                actor,
                "UPDATE_PERSONAL_PROFILE",
                "EMPLOYEE_PERSONAL_PROFILE",
                employeeId,
                "更新员工个人档案：" + employee.getEmployeeNo() + " / " + employee.getName()
        );
        return view(employee, saved);
    }

    private EmployeePersonalProfileDtos.ProfileSummary summary(
            UserAccount employee,
            EmployeePersonalProfile profile
    ) {
        return new EmployeePersonalProfileDtos.ProfileSummary(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                profile == null ? null : profile.getLegalName(),
                employee.getRole(),
                employee.getDepartment(),
                employee.getTitle(),
                employee.getEmployeeStatus() == null ? null : employee.getEmployeeStatus().name(),
                profile == null ? null : profile.getEmploymentType(),
                profile == null ? null : profile.getWorkLocation(),
                profile == null ? null : profile.getUpdatedAt(),
                profile != null
        );
    }

    private EmployeePersonalProfileDtos.ProfileView view(
            UserAccount employee,
            EmployeePersonalProfile profile
    ) {
        String managerName = employee.getManagerId() == null
                ? null
                : userAccountRepository.findById(employee.getManagerId())
                .filter(manager -> manager.getTenantId().equals(employee.getTenantId()))
                .map(UserAccount::getName)
                .orElse(null);
        return new EmployeePersonalProfileDtos.ProfileView(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                profile == null ? employee.getName() : profile.getLegalName(),
                profile == null ? null : profile.getEnglishName(),
                employee.getRole(),
                employee.getDepartment(),
                employee.getTitle(),
                employee.getEmail(),
                employee.getPhone(),
                employee.getEntryDate(),
                employee.getEmployeeStatus() == null ? null : employee.getEmployeeStatus().name(),
                managerName,
                profile == null ? null : profile.getGender(),
                profile == null ? null : profile.getBirthDate(),
                profile == null ? null : profile.getNationality(),
                profile == null ? null : profile.getIdType(),
                profile == null ? "" : secretCryptoService.decrypt(profile.getIdNumberEncrypted()),
                profile == null ? "" : secretCryptoService.decrypt(profile.getPassportNumberEncrypted()),
                profile == null ? null : profile.getPassportExpiryDate(),
                profile == null ? null : profile.getEmploymentType(),
                profile == null ? null : profile.getContractStartDate(),
                profile == null ? null : profile.getContractEndDate(),
                profile == null ? null : profile.getWorkLocation(),
                profile == null ? null : profile.getMonthlySalary(),
                profile == null ? null : profile.getCurrency(),
                profile == null ? null : profile.getHomeAddress(),
                profile == null ? null : profile.getEmergencyContactName(),
                profile == null ? null : profile.getEmergencyContactPhone(),
                profile == null ? null : profile.getUpdatedAt(),
                profile != null
        );
    }

    private UserAccount requireTenantEmployee(Long tenantId, Long employeeId) {
        return userAccountRepository.findById(employeeId)
                .filter(employee -> employee.getTenantId().equals(tenantId))
                .orElseThrow(() -> AppException.notFound("员工档案不存在"));
    }

    private void requireHr(UserAccount actor) {
        if (actor.getRole() != Role.HR) {
            throw AppException.forbidden("只有空间管理员可以维护员工个人信息");
        }
    }

    private void validate(EmployeePersonalProfileDtos.ProfileUpdateRequest request) {
        if (request.contractStartDate() != null
                && request.contractEndDate() != null
                && request.contractEndDate().isBefore(request.contractStartDate())) {
            throw AppException.badRequest("合同结束日期不能早于开始日期");
        }
    }

    private BigDecimal normalizeSalary(BigDecimal salary) {
        return salary == null ? null : salary.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency, BigDecimal salary) {
        String value = clean(currency);
        if (salary == null && value == null) {
            return null;
        }
        return value == null ? "CNY" : value.toUpperCase(Locale.ROOT);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
