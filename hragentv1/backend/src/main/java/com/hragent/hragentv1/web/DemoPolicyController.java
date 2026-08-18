package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.DemoPolicyDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.DemoPolicyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo-policy")
public class DemoPolicyController {
    private final DemoPolicyService demoPolicyService;
    private final AuthService authService;

    public DemoPolicyController(DemoPolicyService demoPolicyService, AuthService authService) {
        this.demoPolicyService = demoPolicyService;
        this.authService = authService;
    }

    @GetMapping("/current")
    public ApiResponse<DemoPolicyDtos.PolicyView> current() {
        return ApiResponse.ok(demoPolicyService.current());
    }

    @PostMapping("/publish-next")
    public ApiResponse<DemoPolicyDtos.PolicyView> publishNext(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        return ApiResponse.ok("演示政策新版已发布", demoPolicyService.publishNext(actor));
    }

    @PostMapping("/reset")
    public ApiResponse<DemoPolicyDtos.PolicyView> reset(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        return ApiResponse.ok("演示政策已重置", demoPolicyService.reset(actor));
    }
}
