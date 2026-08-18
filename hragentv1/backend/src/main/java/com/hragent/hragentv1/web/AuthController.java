package com.hragent.hragentv1.web;

import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.AgentIntegrationDtos;
import com.hragent.hragentv1.dto.AuthDtos;
import com.hragent.hragentv1.dto.UserProfile;
import com.hragent.hragentv1.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<AuthDtos.LoginResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me(HttpServletRequest request) {
        return ApiResponse.ok(authService.currentProfile(request));
    }

    @GetMapping("/workspaces")
    public ApiResponse<java.util.List<com.hragent.hragentv1.dto.WorkspaceDtos.WorkspaceSummary>> workspaces(
            HttpServletRequest request
    ) {
        return ApiResponse.ok(authService.workspaces(request));
    }

    @PostMapping("/profile")
    public ApiResponse<UserProfile> updateProfile(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AuthDtos.ProfileUpdateRequest request
    ) {
        return ApiResponse.ok(authService.updateProfile(servletRequest, request));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(
            HttpServletRequest servletRequest,
            @Valid @RequestBody AuthDtos.PasswordChangeRequest request
    ) {
        authService.changePassword(servletRequest, request);
        return ApiResponse.ok("密码已更新", null);
    }

    @GetMapping("/dingtalk-binding")
    public ApiResponse<AgentIntegrationDtos.BindingStatus> dingtalkBindingStatus(HttpServletRequest request) {
        return ApiResponse.ok(authService.dingtalkBindingStatus(request));
    }

    @PostMapping("/dingtalk-binding/code")
    public ApiResponse<AgentIntegrationDtos.BindingCodeResponse> generateDingtalkBindingCode(
            HttpServletRequest request
    ) {
        return ApiResponse.ok(authService.generateDingtalkBindingCode(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ApiResponse.ok("已退出登录", null);
    }

    @PostMapping("/delete-account")
    public ApiResponse<Void> deleteAccount(
            HttpServletRequest request,
            @Valid @RequestBody AuthDtos.DeleteAccountRequest input
    ) {
        authService.deleteAccount(request, input);
        return ApiResponse.ok("账号已永久注销", null);
    }
}
