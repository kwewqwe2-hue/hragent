package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.Role;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.OnboardingDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/onboarding")
public class OnboardingController {
    private final AuthService authService;
    private final OnboardingService onboardingService;

    public OnboardingController(AuthService authService, OnboardingService onboardingService) {
        this.authService = authService;
        this.onboardingService = onboardingService;
    }

    @PostMapping
    public ApiResponse<OnboardingDtos.RequestView> create(
            HttpServletRequest servletRequest,
            @Valid @RequestBody OnboardingDtos.CreateRequest request
    ) {
        return ApiResponse.ok("入职登记已提交，等待 HR 审核", onboardingService.create(
                authService.requireUser(servletRequest), request
        ));
    }

    @GetMapping("/my")
    public ApiResponse<List<OnboardingDtos.RequestView>> mine(HttpServletRequest request) {
        return ApiResponse.ok(onboardingService.mine(authService.requireUser(request)));
    }

    @PutMapping("/my/office-supplies")
    public ApiResponse<OnboardingDtos.RequestView> completeOfficeSupplies(HttpServletRequest request) {
        return ApiResponse.ok("办公用品领取进度已完成", onboardingService.completeOfficeSupplies(authService.requireUser(request)));
    }

    @GetMapping("/hr/all")
    public ApiResponse<List<OnboardingDtos.RequestView>> hrAll(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(onboardingService.hrAll(actor));
    }

    @GetMapping("/hr/pending")
    public ApiResponse<List<OnboardingDtos.RequestView>> hrPending(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(onboardingService.hrPending(actor));
    }

    @PutMapping("/hr/{id}/review")
    public ApiResponse<OnboardingDtos.RequestView> review(
            HttpServletRequest request,
            @PathVariable Long id,
            @Valid @RequestBody OnboardingDtos.ReviewRequest input
    ) {
        UserAccount actor = authService.requireUser(request);
        authService.requireRole(actor, Role.HR);
        return ApiResponse.ok(onboardingService.review(actor, id, input));
    }
}
