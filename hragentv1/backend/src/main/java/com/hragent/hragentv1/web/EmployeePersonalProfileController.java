package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.EmployeePersonalProfileDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.EmployeePersonalProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/personal-profiles")
public class EmployeePersonalProfileController {
    private final AuthService authService;
    private final EmployeePersonalProfileService profileService;

    public EmployeePersonalProfileController(
            AuthService authService,
            EmployeePersonalProfileService profileService
    ) {
        this.authService = authService;
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ApiResponse<EmployeePersonalProfileDtos.ProfileView> mine(HttpServletRequest request) {
        return ApiResponse.ok(profileService.mine(authService.requireUser(request)));
    }

    @GetMapping
    public ApiResponse<List<EmployeePersonalProfileDtos.ProfileSummary>> list(HttpServletRequest request) {
        return ApiResponse.ok(profileService.list(authService.requireUser(request)));
    }

    @GetMapping("/{employeeId}")
    public ApiResponse<EmployeePersonalProfileDtos.ProfileView> detail(
            HttpServletRequest request,
            @PathVariable Long employeeId
    ) {
        return ApiResponse.ok(profileService.detail(authService.requireUser(request), employeeId));
    }

    @PutMapping("/{employeeId}")
    public ApiResponse<EmployeePersonalProfileDtos.ProfileView> update(
            HttpServletRequest request,
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeePersonalProfileDtos.ProfileUpdateRequest update
    ) {
        UserAccount actor = authService.requireUser(request);
        return ApiResponse.ok(profileService.update(actor, employeeId, update));
    }
}
