package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.*;
import com.hragent.hragentv1.dto.AuthDtos;
import com.hragent.hragentv1.dto.AgentIntegrationDtos;
import com.hragent.hragentv1.dto.UserProfile;
import com.hragent.hragentv1.dto.WorkspaceDtos;
import com.hragent.hragentv1.repo.PlatformAccountRepository;
import com.hragent.hragentv1.repo.TenantRepository;
import com.hragent.hragentv1.repo.UserAccountRepository;
import com.hragent.hragentv1.repo.WorkspaceMembershipRepository;
import com.hragent.hragentv1.web.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final PlatformAccountRepository accountRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, LocalSession> fallbackSessions = new ConcurrentHashMap<>();

    public AuthService(
            PlatformAccountRepository accountRepository,
            WorkspaceMembershipRepository membershipRepository,
            TenantRepository tenantRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            StringRedisTemplate redisTemplate
    ) {
        this.accountRepository = accountRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        PlatformAccount account = accountRepository.findByUsernameIgnoreCase(request.username().trim())
                .filter(PlatformAccount::isActive)
                .orElseThrow(() -> AppException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw AppException.unauthorized("用户名或密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        saveSession(token, account.getId());
        WorkspaceMembership membership = preferredMembership(account.getId());
        return new AuthDtos.LoginResponse(token, profile(account, membership), workspaceSummaries(account.getId()));
    }

    @Transactional
    public AuthDtos.LoginResponse register(AuthDtos.RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        accountRepository.findByUsernameIgnoreCase(username).ifPresent(existing -> {
            throw AppException.badRequest("用户名已存在");
        });
        accountRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
            throw AppException.badRequest("邮箱已被注册");
        });

        PlatformAccount account = new PlatformAccount();
        account.setPublicId(generatePublicId());
        account.setUsername(username);
        account.setEmail(email);
        account.setName(request.name().trim());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        PlatformAccount saved = accountRepository.save(account);

        String token = UUID.randomUUID().toString().replace("-", "");
        saveSession(token, saved.getId());
        return new AuthDtos.LoginResponse(token, profile(saved, null), List.of());
    }

    public PlatformAccount requireAccount(HttpServletRequest request) {
        Long accountId = readSession(extractToken(request));
        return accountRepository.findById(accountId)
                .filter(PlatformAccount::isActive)
                .orElseThrow(() -> AppException.unauthorized("登录已失效，请重新登录"));
    }

    public UserAccount requireUser(HttpServletRequest request) {
        PlatformAccount account = requireAccount(request);
        WorkspaceMembership membership = resolveMembership(request, account.getId(), true);
        if (membership.getStatus() != MembershipStatus.ACTIVE || membership.getEmployeeProfileId() == null) {
            throw AppException.forbidden("当前空间尚未完成员工档案绑定");
        }
        UserAccount user = userAccountRepository.findById(membership.getEmployeeProfileId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> AppException.forbidden("员工档案不可用，请联系空间管理员"));
        if (!user.getTenantId().equals(membership.getWorkspaceId())) {
            throw AppException.forbidden("空间成员数据不一致");
        }
        return user;
    }

    @Transactional
    public AgentIntegrationDtos.BindingCodeResponse generateDingtalkBindingCode(HttpServletRequest request) {
        UserAccount user = requireUser(request);
        String code = String.format("%08d", new java.security.SecureRandom().nextInt(100_000_000));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        user.setDingtalkBindingCodeHash(hashBindingCode(code));
        user.setDingtalkBindingCodeExpiresAt(expiresAt);
        userAccountRepository.save(user);
        return new AgentIntegrationDtos.BindingCodeResponse(code, expiresAt);
    }

    public AgentIntegrationDtos.BindingStatus dingtalkBindingStatus(HttpServletRequest request) {
        UserAccount user = requireUser(request);
        String value = user.getDingtalkUserId();
        String masked = value == null || value.length() < 6
                ? value
                : value.substring(0, 3) + "***" + value.substring(value.length() - 3);
        return new AgentIntegrationDtos.BindingStatus(
                value != null && !value.isBlank(),
                masked,
                user.getDingtalkBoundAt()
        );
    }

    public static String hashBindingCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(code.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash DingTalk binding code", exception);
        }
    }

    public UserProfile currentProfile(HttpServletRequest request) {
        PlatformAccount account = requireAccount(request);
        WorkspaceMembership membership = resolveMembership(request, account.getId(), false);
        return profile(account, membership);
    }

    public List<WorkspaceDtos.WorkspaceSummary> workspaces(HttpServletRequest request) {
        return workspaceSummaries(requireAccount(request).getId());
    }

    public WorkspaceMembership requireWorkspaceRole(
            HttpServletRequest request,
            Long workspaceId,
            Role... roles
    ) {
        PlatformAccount account = requireAccount(request);
        WorkspaceMembership membership = membershipRepository.findByAccountIdAndWorkspaceId(account.getId(), workspaceId)
                .filter(item -> item.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(() -> AppException.forbidden("当前账号不属于该空间"));
        for (Role role : roles) {
            if (membership.getRole() == role) {
                return membership;
            }
        }
        throw AppException.forbidden("当前账号没有该空间的管理权限");
    }

    public PlatformAccount requirePlatformAdmin(HttpServletRequest request) {
        PlatformAccount account = requireAccount(request);
        if (!account.isPlatformAdmin()) {
            throw AppException.forbidden("当前账号不是平台管理员");
        }
        return account;
    }

    public void requireRole(UserAccount user, Role... roles) {
        for (Role role : roles) {
            if (user.getRole() == role) {
                return;
            }
        }
        throw AppException.forbidden("当前账号没有权限执行此操作");
    }

    @Transactional
    public UserProfile updateProfile(HttpServletRequest request, AuthDtos.ProfileUpdateRequest update) {
        PlatformAccount account = requireAccount(request);
        String email = update.email().trim().toLowerCase();
        accountRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(account.getId()))
                .ifPresent(existing -> {
                    throw AppException.badRequest("邮箱已被其他账号使用");
                });
        account.setName(update.name().trim());
        account.setEmail(email);
        account.setAvatarUrl(update.avatarUrl() == null || update.avatarUrl().isBlank() ? null : update.avatarUrl().trim());
        account.setUpdatedAt(LocalDateTime.now());
        PlatformAccount saved = accountRepository.save(account);

        for (WorkspaceMembership membership : membershipRepository.findByAccountIdOrderByCreatedAtDesc(saved.getId())) {
            if (membership.getEmployeeProfileId() != null) {
                userAccountRepository.findById(membership.getEmployeeProfileId()).ifPresent(profile -> {
                    profile.setName(saved.getName());
                    profile.setEmail(saved.getEmail());
                    userAccountRepository.save(profile);
                });
            }
        }
        return profile(saved, resolveMembership(request, saved.getId(), false));
    }

    @Transactional
    public void changePassword(HttpServletRequest request, AuthDtos.PasswordChangeRequest update) {
        PlatformAccount account = requireAccount(request);
        if (!passwordEncoder.matches(update.currentPassword(), account.getPasswordHash())) {
            throw AppException.badRequest("当前密码不正确");
        }
        account.setPasswordHash(passwordEncoder.encode(update.newPassword()));
        account.setUpdatedAt(LocalDateTime.now());
        accountRepository.save(account);
    }

    public void logout(HttpServletRequest request) {
        String token = extractToken(request);
        fallbackSessions.remove(token);
        try {
            redisTemplate.delete(redisKey(token));
        } catch (RedisConnectionFailureException ignored) {
            // Local fallback keeps logout usable if Redis is unavailable.
        }
    }

    private UserProfile profile(PlatformAccount account, WorkspaceMembership membership) {
        Tenant workspace = membership == null ? null : tenantRepository.findById(membership.getWorkspaceId()).orElse(null);
        UserAccount employee = membership == null || membership.getEmployeeProfileId() == null
                ? null
                : userAccountRepository.findById(membership.getEmployeeProfileId()).orElse(null);
        return UserProfile.from(account, membership, employee, workspace);
    }

    private List<WorkspaceDtos.WorkspaceSummary> workspaceSummaries(Long accountId) {
        return membershipRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(membership -> {
                    Tenant workspace = tenantRepository.findById(membership.getWorkspaceId()).orElse(null);
                    if (workspace == null) {
                        return null;
                    }
                    return new WorkspaceDtos.WorkspaceSummary(
                            workspace.getId(),
                            workspace.getName(),
                            workspace.getCode(),
                            membership.getRole(),
                            membership.getStatus(),
                            membership.getEmployeeProfileId(),
                            membershipRepository.countByWorkspaceIdAndStatus(workspace.getId(), MembershipStatus.ACTIVE)
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private WorkspaceMembership preferredMembership(Long accountId) {
        List<WorkspaceMembership> memberships = membershipRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        return memberships.stream().filter(item -> item.getStatus() == MembershipStatus.ACTIVE).findFirst()
                .orElse(memberships.isEmpty() ? null : memberships.get(0));
    }

    private WorkspaceMembership resolveMembership(HttpServletRequest request, Long accountId, boolean required) {
        String header = request.getHeader("X-Workspace-Id");
        if (header != null && !header.isBlank()) {
            try {
                Long workspaceId = Long.valueOf(header.trim());
                return membershipRepository.findByAccountIdAndWorkspaceId(accountId, workspaceId)
                        .orElseThrow(() -> AppException.forbidden("当前账号不属于所选空间"));
            } catch (NumberFormatException exception) {
                throw AppException.badRequest("X-Workspace-Id 格式不正确");
            }
        }
        WorkspaceMembership preferred = preferredMembership(accountId);
        if (preferred == null && required) {
            throw AppException.forbidden("请先创建或加入一个空间");
        }
        return preferred;
    }

    private String generatePublicId() {
        String value;
        do {
            value = "USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        } while (accountRepository.findByPublicId(value).isPresent());
        return value;
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw AppException.unauthorized("请先登录");
        }
        return header.substring("Bearer ".length()).trim();
    }

    private void saveSession(String token, Long accountId) {
        fallbackSessions.put(token, new LocalSession(accountId, Instant.now().plus(SESSION_TTL)));
        try {
            redisTemplate.opsForValue().set(redisKey(token), String.valueOf(accountId), SESSION_TTL);
        } catch (RedisConnectionFailureException ignored) {
            // Local fallback keeps the app usable when Redis is unavailable.
        }
    }

    private Long readSession(String token) {
        try {
            String value = redisTemplate.opsForValue().get(redisKey(token));
            if (value != null) {
                return Long.valueOf(value);
            }
        } catch (RedisConnectionFailureException ignored) {
            // Fall through to the in-memory development session.
        }
        LocalSession session = fallbackSessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            fallbackSessions.remove(token);
            throw new AppException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
        return session.accountId();
    }

    private String redisKey(String token) {
        return "hragent:v2:session:" + token;
    }

    private record LocalSession(Long accountId, Instant expiresAt) {
    }
}
