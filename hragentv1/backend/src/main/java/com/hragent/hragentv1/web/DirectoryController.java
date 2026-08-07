package com.hragent.hragentv1.web;

import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.DirectoryDtos;
import com.hragent.hragentv1.service.AuthService;
import com.hragent.hragentv1.service.DirectoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/directory")
public class DirectoryController {
    private final AuthService authService;
    private final DirectoryService directoryService;

    public DirectoryController(AuthService authService, DirectoryService directoryService) {
        this.authService = authService;
        this.directoryService = directoryService;
    }

    @GetMapping
    public ApiResponse<DirectoryDtos.DirectoryOverview> overview(HttpServletRequest request) {
        UserAccount actor = authService.requireUser(request);
        return ApiResponse.ok(directoryService.overview(actor));
    }

    @GetMapping("/employees/{id}")
    public ApiResponse<DirectoryDtos.EmployeeDetail> employeeDetail(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        UserAccount actor = authService.requireUser(request);
        return ApiResponse.ok(directoryService.employeeDetail(actor, id));
    }
}
