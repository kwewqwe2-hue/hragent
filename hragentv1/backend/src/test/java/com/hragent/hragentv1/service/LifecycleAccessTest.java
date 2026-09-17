package com.hragent.hragentv1.service;
import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LifecycleAccessTest {
 @Test void formerEmployeeAccessDoesNotReactivateWorkspacePrivileges(){
  var accounts=mock(PlatformAccountRepository.class);var memberships=mock(WorkspaceMembershipRepository.class);var users=mock(UserAccountRepository.class);
  var redis=mock(StringRedisTemplate.class);ValueOperations<String,String> ops=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(ops);when(ops.get(anyString())).thenReturn("10");
  var auth=new AuthService(accounts,memberships,mock(TenantRepository.class),users,mock(PasswordEncoder.class),redis);
  var account=new PlatformAccount();org.springframework.test.util.ReflectionTestUtils.setField(account,"id",10L);account.setActive(true);when(accounts.findById(10L)).thenReturn(Optional.of(account));
  var membership=new WorkspaceMembership();membership.setAccountId(10L);membership.setWorkspaceId(1L);membership.setEmployeeProfileId(2L);membership.setStatus(MembershipStatus.LEFT);when(memberships.findByAccountIdAndWorkspaceId(10L,1L)).thenReturn(Optional.of(membership));
  var employee=new UserAccount();employee.setId(2L);employee.setAccountId(10L);employee.setTenantId(1L);employee.setActive(false);employee.setEmployeeStatus(EmployeeStatus.LEFT);when(users.findById(2L)).thenReturn(Optional.of(employee));
  var request=new MockHttpServletRequest();request.addHeader("Authorization","Bearer test");request.addHeader("X-Workspace-Id","1");
  assertThat(auth.requireLifecycleUser(request)).isSameAs(employee);
  assertThatThrownBy(()->auth.requireUser(request)).hasMessageContaining("档案绑定");
  employee.setAccountId(99L);assertThatThrownBy(()->auth.requireLifecycleUser(request)).hasMessageContaining("关联不一致");
  employee.setAccountId(10L);employee.setEmployeeStatus(EmployeeStatus.INACTIVE);assertThatThrownBy(()->auth.requireLifecycleUser(request)).hasMessageContaining("档案绑定");
  employee.setEmployeeStatus(EmployeeStatus.LEFT);account.setActive(false);assertThatThrownBy(()->auth.requireLifecycleUser(request)).hasMessageContaining("登录已失效");
 }
}

