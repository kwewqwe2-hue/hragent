package com.hragent.hragentv1.web;

import com.hragent.hragentv1.dto.ApiResponse;
import com.hragent.hragentv1.dto.PlatformDtos;
import com.hragent.hragentv1.service.PlatformAdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/platform-admin")
public class PlatformAdminController {
    private final PlatformAdminService platformAdminService;

    public PlatformAdminController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/workspaces")
    public ApiResponse<List<PlatformDtos.WorkspaceOverview>> workspaces(HttpServletRequest request) {
        return ApiResponse.ok(platformAdminService.workspaces(request));
    }

    @GetMapping("/workspaces/{workspaceId}")
    public ApiResponse<PlatformDtos.WorkspaceDetail> workspace(
            HttpServletRequest request,
            @PathVariable Long workspaceId
    ) {
        return ApiResponse.ok(platformAdminService.workspace(request, workspaceId));
    }
}
