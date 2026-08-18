package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.DemoPolicySourceState;
import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.DemoPolicyDtos;
import com.hragent.hragentv1.repo.DemoPolicySourceStateRepository;
import com.hragent.hragentv1.web.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoPolicyServiceTest {
    @Mock
    private DemoPolicySourceStateRepository stateRepository;
    @Mock
    private AuditService auditService;

    private DemoPolicyService service;
    private AtomicReference<DemoPolicySourceState> storedState;

    @BeforeEach
    void setUp() {
        service = new DemoPolicyService(stateRepository, auditService);
        storedState = new AtomicReference<>();
    }

    private void stubStateRepository() {
        when(stateRepository.findById(1L))
                .thenAnswer(invocation -> Optional.ofNullable(storedState.get()));
        when(stateRepository.save(any(DemoPolicySourceState.class)))
                .thenAnswer(invocation -> {
                    DemoPolicySourceState state = invocation.getArgument(0);
                    storedState.set(state);
                    return state;
                });
    }

    @Test
    void hrCanPublishAChangedVersionWithADifferentHash() {
        stubStateRepository();
        DemoPolicyDtos.PolicyView initial = service.current();

        DemoPolicyDtos.PolicyView updated = service.publishNext(user(Role.HR));

        assertThat(initial.version()).isEqualTo("2026.1");
        assertThat(initial.updateAvailable()).isTrue();
        assertThat(updated.version()).isEqualTo("2026.2");
        assertThat(updated.updateAvailable()).isFalse();
        assertThat(updated.contentHash()).isNotEqualTo(initial.contentHash());
        verify(auditService).log(
                any(UserAccount.class),
                org.mockito.ArgumentMatchers.eq("PUBLISH_DEMO_POLICY"),
                org.mockito.ArgumentMatchers.eq("demo_policy_source"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.contains("2026.2")
        );
    }

    @Test
    void employeeCannotPublishDemoPolicy() {
        assertThatThrownBy(() -> service.publishNext(user(Role.EMPLOYEE)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("空间管理员");
    }

    private UserAccount user(Role role) {
        UserAccount user = new UserAccount();
        user.setId(3L);
        user.setTenantId(1L);
        user.setName("Demo User");
        user.setRole(role);
        return user;
    }
}
