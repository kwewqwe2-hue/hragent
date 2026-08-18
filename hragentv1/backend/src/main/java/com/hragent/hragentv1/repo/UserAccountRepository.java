package com.hragent.hragentv1.repo;

import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByTenantIdAndEmployeeNo(Long tenantId, String employeeNo);

    Optional<UserAccount> findByTenantIdAndAccountId(Long tenantId, Long accountId);

    Optional<UserAccount> findByTenantIdAndDingtalkUserId(Long tenantId, String dingtalkUserId);

    List<UserAccount> findByTenantIdAndDingtalkBindingCodeHash(Long tenantId, String dingtalkBindingCodeHash);

    List<UserAccount> findByTenantIdOrderByIdAsc(Long tenantId);

    List<UserAccount> findByTenantIdAndRole(Long tenantId, Role role);

    long countByTenantIdAndManagerIdAndActiveTrue(Long tenantId, Long managerId);
}
